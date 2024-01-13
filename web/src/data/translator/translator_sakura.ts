import { Llamacpp } from './api/llamacpp';
import { OpenAi } from './api/openai';
import { createLengthSegmentor } from './common';
import { BaseTranslatorConfig, Glossary, SegmentTranslator } from './type';

export interface SakuraTranslatorConfig extends BaseTranslatorConfig {
  endpoint: string;
  useLlamaApi: boolean;
}

export class SakuraTranslator implements SegmentTranslator {
  glossary: Glossary;
  log: (message: string) => void;

  private api: Llamacpp | OpenAi;
  model: SakuraModel = { version: '0.8' };

  constructor({
    client,
    glossary,
    log,
    endpoint,
    useLlamaApi,
  }: SakuraTranslatorConfig) {
    this.glossary = glossary;
    this.log = log;

    if (useLlamaApi) {
      this.api = new Llamacpp(client, endpoint);
    } else {
      this.api = new OpenAi(client, endpoint, 'no-key');
    }
  }

  createSegments = createLengthSegmentor(500);

  async init() {
    let model: SakuraModel;
    if (this.api.id === 'llamacpp') {
      model = await SakuraLlamacpp.detectModel(this.api);
    } else {
      model = await SakuraOpenai.detectModel(this.api);
    }
    this.model = model;
    return this;
  }

  allowUpload = () => {
    if (this.model.distance === undefined) {
      return false;
    } else {
      return [this.model.distance.v8q4, this.model.distance.v8q5].some(
        (d) => d < 0.01
      );
    }
  };

  async translate(
    seg: string[],
    segInfo: { index: number; size: number }
  ): Promise<string[]> {
    const newSeg = seg.map((text) => {
      for (const wordJp in this.glossary) {
        const wordZh = this.glossary[wordJp];
        text = text.replaceAll(wordJp, wordZh);
      }
      return text;
    });
    return this.translateInner(newSeg, segInfo);
  }

  async translateInner(
    seg: string[],
    segInfo: { index: number; size: number }
  ): Promise<string[]> {
    const maxNewToken = 1000;
    const concatedSeg = seg.join('\n');

    let retry = 0;
    while (retry < 2) {
      const { text, hasDegradation } = await this.translatePrompt(
        concatedSeg,
        maxNewToken,
        retry > 0
      );
      const splitText = text.split('\n');

      this.log(
        `分段${segInfo.index + 1}/${segInfo.size}[${retry}] ${
          hasDegradation ? ' 退化' : ''
        }`
      );

      if (!hasDegradation && seg.length === splitText.length) {
        return splitText;
      } else {
        retry += 1;
      }
    }

    // 进入逐行翻译模式
    this.log(`分段${segInfo.index + 1}/${segInfo.size}[逐行翻译]`);
    const resultPerLine = [];
    for (const line of seg) {
      const { text, hasDegradation } = await this.translatePrompt(
        line,
        maxNewToken,
        true
      );
      if (hasDegradation) resultPerLine.push(line);
      else resultPerLine.push(text);
    }
    return resultPerLine;
  }

  private translatePrompt(
    text: string,
    maxNewToken: number,
    tryFixDegradation: boolean
  ) {
    if (this.api.id === 'llamacpp') {
      return SakuraLlamacpp.translateText(
        this.api,
        this.model.version,
        text,
        maxNewToken,
        tryFixDegradation
      );
    } else {
      return SakuraOpenai.translateText(
        this.api,
        text,
        maxNewToken,
        tryFixDegradation
      );
    }
  }
}

interface SakuraModel {
  version: '0.8' | '0.9';
  distance?: {
    v8q4: number;
    v8q5: number;
  };
}

namespace SakuraLlamacpp {
  const makePrompt = (text: string, version: '0.8' | '0.9') => {
    if (version === '0.9') {
      return `<|im_start|>system\n你是一个轻小说翻译模型，可以流畅通顺地以日本轻小说的风格将日文翻译成简体中文 ，并联系上下文正确使用人称代词，不擅自添加原文中没有的代词。<|im_end|>\n<|im_start|>user\n将下面的日文文本翻译成中文：${text}<|im_end|>\n<|im_start|>assistant\n`;
    } else {
      return `<reserved_106>将下面的日文文本翻译成中文：${text}<reserved_107>`;
    }
  };

  export const detectModel = (api: Llamacpp): Promise<SakuraModel> =>
    api
      .createCompletion(
        {
          prompt: makePrompt('国境の長いトンネルを抜けると雪国であった', '0.8'),
          n_predict: 20,
          n_probs: 1,
          seed: 0,
        },
        {
          timeout: false,
        }
      )
      .then((completion) => {
        const version: '0.8' | '0.9' = completion.model.includes('0.9')
          ? '0.9'
          : '0.8';

        if (
          completion.completion_probabilities === undefined ||
          completion.completion_probabilities.length === 0
        ) {
          return { version };
        }

        const fingerprintVectorKnown = {
          v8q4: [
            0.4115177392959595, 0.6806630492210388, 0.8027622103691101,
            0.36451900005340576, 0.8880155682563782, 0.5641778707504272,
            0.4389311969280243, 0.3928905129432678, 0.7579050660133362,
            0.9564539194107056, 1, 1,
          ],
          v8q5: [
            0.4395886957645416, 0.6967197060585022, 0.9305418729782104,
            0.5735085606575012, 0.5760149955749512, 1, 0.45953643321990967,
            0.9667923450469971, 0.22573211789131165, 0.23779834806919098, 1,
            0.3153495490550995, 0.3401365280151367, 1, 1,
          ],
        };

        const fingerprintVector = completion.completion_probabilities.map(
          (it) => it.probs[0].prob
        );

        const calculateDistance = (a: number[], b: number[]) => {
          if (a.length !== b.length) {
            return Infinity;
          }
          let d = 0;
          for (let i = 0; i < a.length; i++) {
            const numA = a[i];
            const numB = b.at(i) ?? 0;
            d += Math.abs(numA - numB) ** 2;
          }
          return d;
        };

        const distance = {
          v8q4: calculateDistance(
            fingerprintVectorKnown.v8q4,
            fingerprintVector
          ),
          v8q5: calculateDistance(
            fingerprintVectorKnown.v8q5,
            fingerprintVector
          ),
        };

        return { version, distance };
      });

  export const translateText = (
    api: Llamacpp,
    version: '0.8' | '0.9',
    text: string,
    maxNewToken: number,
    tryFixDegradation: boolean
  ) =>
    api
      .createCompletion(
        {
          prompt: makePrompt(text, version),
          n_predict: maxNewToken,
          temperature: 0.1,
          top_p: 0.3,
          top_k: 40,
          repeat_penalty: 1.0,
          frequency_penalty: tryFixDegradation ? 0.2 : 0.0,
        },
        {
          timeout: false,
        }
      )
      .then(({ content, model, stopped_limit }) => ({
        text: content,
        model,
        hasDegradation: stopped_limit,
      }));
}

namespace SakuraOpenai {
  const createChatCompletions = (
    api: OpenAi,
    text: string,
    config: { max_tokens: number; frequency_penalty?: number }
  ) =>
    api.createChatCompletions(
      {
        model: 'sukinishiro',
        messages: [
          {
            role: 'system',
            content:
              '你是一个轻小说翻译模型，可以流畅通顺地以日本轻小说的风格将日文翻译成简体中文，并联系上下文正确使用人称代词，不擅自添加原文中没有的代词。',
          },
          {
            role: 'user',
            content: '将下面的日文文本翻译成中文：' + text,
          },
        ],
        temperature: 0.1,
        top_p: 0.3,
        ...config,

        //
        do_sample: true,
        top_k: 40,
        num_beams: 1,
        repetition_penalty: 1.0,
      } as any,
      {
        timeout: false,
      }
    );

  export const detectModel = (api: OpenAi): Promise<SakuraModel> =>
    createChatCompletions(api, '国境の長いトンネルを抜けると雪国であった', {
      max_tokens: 20,
    }).then((completion) => {
      const version: '0.8' | '0.9' = completion.model.includes('0.9')
        ? '0.9'
        : '0.8';
      const allowUpload = completion.model.includes('0.8-Q4');
      // TODO: 等待sakura支持返回概率
      return { version, distance8Q4: allowUpload ? 0 : Infinity };
    });

  export const translateText = (
    api: OpenAi,
    text: string,
    maxNewToken: number,
    tryFixDegradation: boolean
  ) =>
    createChatCompletions(api, text, {
      max_tokens: maxNewToken,
      frequency_penalty: tryFixDegradation ? 0.2 : 0.0,
    }).then((completion) => ({
      text: completion.choices[0].message.content!!,
      model: completion.model,
      hasDegradation: completion.choices[0].finish_reason !== 'stop',
    }));
}
