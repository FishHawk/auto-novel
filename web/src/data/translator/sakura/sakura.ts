import { KyInstance } from 'ky/distribution/types/ky';

import { parseEventStream } from '../openai/common';
import { createLengthSegmentor } from '../tradition/common';
import { BaseTranslatorConfig, Glossary, SegmentTranslator } from '../type';
import { LlamaApi } from './api_llama';

export interface SakuraTranslatorConfig extends BaseTranslatorConfig {
  endpoint: string;
  useLlamaApi: boolean;
}

export class SakuraTranslator implements SegmentTranslator {
  private client: KyInstance;
  glossary: Glossary;
  log: (message: string) => void;

  private endpoint: string;
  private api: LlamaApi | undefined;
  private version: '0.8' | '0.9' = '0.8';

  constructor({
    client,
    glossary,
    log,
    endpoint,
    useLlamaApi,
  }: SakuraTranslatorConfig) {
    this.client = client.create({
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
    });
    this.glossary = glossary;
    this.log = log;

    this.endpoint = endpoint;
    if (useLlamaApi) {
      this.api = new LlamaApi(client, endpoint);
    } else {
      this.api = undefined;
    }
  }

  createSegments = createLengthSegmentor(500);

  async init() {
    if (this.api === undefined) {
      return this;
    }
    const result = await this.translatePrompt('test', 1, {});
    if (result.model) {
      if (result.model.includes('0.8')) {
        this.version = '0.8';
      } else if (result.model.includes('0.9')) {
        this.version = '0.9';
      } else {
        throw '不支持的版本';
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
    const prompt = this.makePrompt(seg.join('\n'));

    let retry = 0;
    while (retry < 2) {
      const { text, hasDegradation } = await this.translatePrompt(
        prompt,
        maxNewToken,
        retry === 0 ? {} : { frequency_penalty: 0.2 }
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
      const prompt = this.makePrompt(line);
      const { text, hasDegradation } = await this.translatePrompt(
        prompt,
        maxNewToken,
        { frequency_penalty: 0.2 }
      );
      if (hasDegradation) resultPerLine.push(line);
      else resultPerLine.push(text);
    }
    return resultPerLine;
  }

  private async translatePrompt(
    prompt: string,
    maxNewToken: number,
    config: any
  ) {
    if (this.api) {
      const { content, model, stopped_limit } = await this.api.createCompletion(
        {
          prompt,
          n_predict: maxNewToken,
          temperature: 0.1,
          top_p: 0.3,
          top_k: 40,
          repeat_penalty: 1.0,
          ...config,
        },
        {
          timeout: false,
        }
      );
      return { text: content, model, hasDegradation: stopped_limit };
    } else {
      const response = this.client.post(this.endpoint, {
        json: {
          prompt,
          preset: 'None',
          max_new_tokens: maxNewToken,
          seed: -1,
          do_sample: true,
          temperature: 0.1,
          top_p: 0.3,
          top_k: 40,
          num_beams: 1,
          repetition_penalty: 1.0,
          ...config,
        },
        timeout: false,
      });
      let obj: SakuraResultChunk | undefined = undefined;
      if (this.endpoint.includes('stream')) {
        const stream = await response
          .text()
          .then(parseEventStream<SakuraResultChunk>);
        Array.from(stream).forEach((it) => (obj = it));
      } else {
        obj = await response.json();
      }
      if (obj === undefined) throw 'quit';
      const { text, new_token } = obj['results'][0] as {
        text: string;
        new_token: number;
      };
      const hasDegradation = new_token >= maxNewToken;
      return { text, hasDegradation };
    }
  }

  private makePrompt = (textToTranslate: string) => {
    if (this.version === '0.8') {
      return `<reserved_106>将下面的日文文本翻译成中文：${textToTranslate}<reserved_107>`;
    } else if (this.version === '0.9') {
      return `<|im_start|>system\n你是一个轻小说翻译模型，可以流畅通顺地以日本轻小说的风格将日文翻译成简体中文 ，并联系上下文正确使用人称代词，不擅自添加原文中没有的代词。<|im_end|>\n<|im_start|>user\n将下面的日文文本翻译成中文：${textToTranslate}<|im_end|>\n<|im_start|>assistant\n`;
    } else {
      throw 'quit';
    }
  };
}

interface SakuraResultChunk {
  results: Array<{
    new_token: number;
    text: string;
  }>;
}
