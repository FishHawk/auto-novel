import { KyInstance } from 'ky/distribution/types/ky';

import { BaseTranslatorConfig, Glossary, SegmentTranslator } from '../type';
import { createLengthSegmentor } from '../tradition/common';
import { parseEventStream } from '../openai/common';
import { h } from 'vue';

export interface SakuraTranslatorConfig extends BaseTranslatorConfig {
  endpoint: string;
  useLlamaApi: boolean;
}

export class SakuraTranslator implements SegmentTranslator {
  private client: KyInstance;
  glossary: Glossary;
  log: (message: string) => void;

  private endpoint: string;
  private useLlamaApi: boolean;

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
    this.useLlamaApi = useLlamaApi;
  }

  createSegments = createLengthSegmentor(500);

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
    const maxNewToken = 1024;
    const prompt = makePrompt(seg.join('\n'));

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
      const prompt = makePrompt(line);
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
    if (this.useLlamaApi) {
      return this.translatePromptWithLlamaApi(prompt, maxNewToken, config);
    } else {
      return this.translatePromptWithoutLlamaApi(prompt, maxNewToken, config);
    }
  }

  private async translatePromptWithoutLlamaApi(
    prompt: string,
    maxNewToken: number,
    config: any
  ) {
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

  private async translatePromptWithLlamaApi(
    prompt: string,
    maxNewToken: number,
    config: any
  ) {
    const response = this.client.post(this.endpoint + '/completion', {
      json: {
        prompt,
        n_predict: maxNewToken,
        temperature: 0.1,
        top_p: 0.3,
        top_k: 40,
        repeat_penalty: 1.0,
        seed: -1,
        ...config,
      },
      timeout: false,
    });
    let { content, model, truncated } =
      await response.json<SakuraLlamaCompletionResponse>();
    return { text: content, hasDegradation: truncated };
  }
}

const makePrompt = (textToTranslate: string) =>
  `<reserved_106>将下面的日文文本翻译成中文：${textToTranslate}<reserved_107>`;

interface SakuraResultChunk {
  results: Array<{
    new_token: number;
    text: string;
  }>;
}

interface SakuraLlamaCompletionResponse {
  content: string;
  model: string;
  truncated: boolean;
}
