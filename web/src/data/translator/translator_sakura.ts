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
  private version: '0.8' | '0.9' = '0.8';

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
    if (this.api.id === 'llamacpp') {
      const result = await this.translatePrompt('test', 1, false);
      if (result.model) {
        if (result.model.includes('0.8')) {
          this.version = '0.8';
        } else if (result.model.includes('0.9')) {
          this.version = '0.9';
        } else {
          throw '不支持的版本';
        }
      }
    }
    return this;
  }

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
      return askLlamacpp(
        this.api,
        this.version,
        text,
        maxNewToken,
        tryFixDegradation
      );
    } else {
      return askOpenai(this.api, text, maxNewToken, tryFixDegradation);
    }
  }
}

const askLlamacpp = (
  api: Llamacpp,
  version: '0.8' | '0.9',
  text: string,
  maxNewToken: number,
  tryFixDegradation: boolean
) => {
  const makePrompt = () => {
    if (version === '0.8') {
      return `<reserved_106>将下面的日文文本翻译成中文：${text}<reserved_107>`;
    } else if (version === '0.9') {
      return `<|im_start|>system\n你是一个轻小说翻译模型，可以流畅通顺地以日本轻小说的风格将日文翻译成简体中文 ，并联系上下文正确使用人称代词，不擅自添加原文中没有的代词。<|im_end|>\n<|im_start|>user\n将下面的日文文本翻译成中文：${text}<|im_end|>\n<|im_start|>assistant\n`;
    } else {
      throw 'quit';
    }
  };

  return api
    .createCompletion(
      {
        prompt: makePrompt(),
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
};

const askOpenai = (
  api: OpenAi,
  text: string,
  maxNewToken: number,
  tryFixDegradation: boolean
) =>
  api
    .createChatCompletions(
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
        max_tokens: maxNewToken,
        frequency_penalty: tryFixDegradation ? 0.2 : 0.0,

        //
        do_sample: true,
        top_k: 40,
        num_beams: 1,
        repetition_penalty: 1.0,
      } as any,
      {
        timeout: false,
      }
    )
    .then((completion) => ({
      text: completion.choices[0].message.content!!,
      model: completion.model,
      hasDegradation: completion.choices[0].finish_reason !== 'stop',
    }));
