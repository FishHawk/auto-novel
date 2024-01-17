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
  log: (message: string, detail?: string[]) => void;

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
      return [this.model.distance.v9q4].some((d) => d < 0.01);
    }
  };

  async translate(
    seg: string[],
    segInfo: { index: number; size: number }
  ): Promise<string[]> {
    const newSeg = seg
      .map((text) =>
        // 全角数字转换成半角数字
        text.replace(/[\uff10-\uff19]/g, (ch) =>
          String.fromCharCode(ch.charCodeAt(0) - 0xfee0)
        )
      )
      .map((text) => {
        const wordJpArray = Object.keys(this.glossary).sort(
          (a, b) => b.length - a.length
        );
        for (const wordJp of wordJpArray) {
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
      const splitText = text.replaceAll('<|im_end|>', '').split('\n');

      this.log(
        `分段${segInfo.index + 1}/${segInfo.size}[${retry}] ${
          hasDegradation ? ' 退化' : ''
        }`,
        [seg.join('\n'), text]
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
      if (hasDegradation) {
        throw Error('发生单行退化，Sakura翻译器可能存在异常');
      } else {
        resultPerLine.push(text.replaceAll('<|im_end|>', ''));
      }
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
    v9q4: number;
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
          prompt: makePrompt('国境の長いトンネルを抜けると雪国であった', '0.9'),
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
          v9q4: [
            0.48827865719795227, 1, 1, 0.8033697009086609, 0.6872135400772095,
            1, 0.734160304069519, 0.1770634949207306, 0.39328014850616455,
            0.9504808783531189, 0.8134298324584961, 0.5873062014579773, 1, 1,
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
          v9q4: calculateDistance(
            fingerprintVectorKnown.v9q4,
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
      // TODO: 等待sakura支持返回概率
      return {
        version,
        distance: {
          v9q4: completion.model.includes('0.9-Q4') ? 0 : Infinity,
        },
      };
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
