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
    if (this.model.fingerprint === undefined) {
      return false;
    } else if (this.model.fingerprint === 'placeholder-allow') {
      return true;
    } else {
      const fingerprint = this.model.fingerprint;
      const fingerprintKnownList = [
        // V9Q4
        [
          0.43284669518470764, 0.4130252003669739, 0.04670359566807747,
          0.026198342442512512, 0.018005838617682457, 0.017726685851812363,
          0.013591521419584751, 0.0112677663564682, 0.010921093635261059,
          0.009713421575725079,
        ],
        // V9Q4 awq
        [
          0.4408239424228668, 0.42726120352745056, 0.04197537526488304,
          0.029075490310788155, 0.013842009007930756, 0.01270214095711708,
          0.008798516355454922, 0.008730045519769192, 0.008395601995289326,
          0.008395601995289326,
        ],
        // V9bQ4
        [
          0.4376196265220642, 0.40473198890686035, 0.046124767512083054,
          0.042326465249061584, 0.014513801783323288, 0.013741392642259598,
          0.012511679902672768, 0.010453899390995502, 0.009444311261177063,
          0.008532224223017693,
        ],
        // V9bQ4 awq
        [
          0.5258093476295471, 0.35578039288520813, 0.039606813341379166,
          0.023650309070944786, 0.010912824422121048, 0.009858915582299232,
          0.00970606692135334, 0.009118006564676762, 0.008173327893018723,
          0.007383986376225948,
        ],
        // V9Q5
        [
          0.47926297783851624, 0.3850986361503601, 0.048578787595033646,
          0.025202343240380287, 0.015049006789922714, 0.011538472957909107,
          0.010755039751529694, 0.009199273772537708, 0.007807321380823851,
          0.007508228067308664,
        ],
      ];

      const calculateDistance = (a: number[], b: number[]) => {
        let d = 0;
        for (let i = 0; i < a.length; i++) {
          const numA = a[i];
          const numB = b.at(i) ?? 0;
          d += Math.abs(numA - numB) ** 2;
        }
        return d;
      };

      const distanceList = fingerprintKnownList.map((known) => {
        const distance = calculateDistance(known, fingerprint);
        console.log(distance);
        return distance;
      });
      return distanceList.some((it) => it < 0.001);
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
      const { text, hasDegradation, extra } = await this.translatePrompt(
        concatedSeg,
        maxNewToken,
        retry > 0
      );
      const splitText = text.replaceAll('<|im_end|>', '').split('\n');

      const detail = [seg.join('\n'), text];
      if (extra !== undefined) {
        detail.push(JSON.stringify(extra, null, 2));
      }
      this.log(
        `分段${segInfo.index + 1}/${segInfo.size}[${retry}] ${
          hasDegradation ? ' 退化' : ''
        }`,
        detail
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
  fingerprint?: number[] | 'placeholder-allow';
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
          temperature: 1,
          top_p: 1,
          n_predict: 1,
          n_probs: 10,
          min_keep: 10,
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
          completion.completion_probabilities.length === 0 ||
          version === '0.8'
        ) {
          return { version };
        }

        const fingerprint = completion.completion_probabilities[0].probs.map(
          (it) => it.prob
        );

        return { version, fingerprint };
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
      .then(({ prompt, generation_settings, content, stopped_limit }) => ({
        text: content,
        hasDegradation: stopped_limit,
        extra: {
          prompt,
          generation_settings,
        },
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
      const allow = [
        '0.9-Q4',
        '0.9-Q5',
        '0.9-Q6',
        '0.9-Q8',
        '0.9b-Q4',
        '0.9b-Q5',
        '0.9b-Q6',
        '0.9b-Q8',
      ].some((it) => completion.model.includes(it));
      return {
        version,
        fingerprint: allow ? 'placeholder-allow' : undefined,
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
      hasDegradation: completion.choices[0].finish_reason !== 'stop',
      extra: undefined,
    }));
}
