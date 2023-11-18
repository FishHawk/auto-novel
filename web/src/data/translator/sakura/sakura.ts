import { KyInstance } from 'ky/distribution/types/ky';

import { Glossary, SegmentTranslator } from '../type';
import { createLengthSegmentor } from '../tradition/common';
import { parseEventStream } from '../openai/common';

export class SakuraTranslator implements SegmentTranslator {
  log: (message: string) => void;
  glossary: Glossary;

  private client: KyInstance;
  private endpoint: string;

  constructor(
    client: KyInstance,
    log: (message: string) => void,
    glossary: Glossary,
    endpoint: string
  ) {
    this.client = client.create({
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
    });
    this.endpoint = endpoint;
    this.log = log;
    this.glossary = glossary;
  }

  createSegments = createLengthSegmentor(500);

  async translate(
    seg: string[],
    segInfo: { index: number; size: number }
  ): Promise<string[]> {
    const maxNewToken = 1024;
    const prompt = makePrompt(seg.join('\n'));

    let retry = 0;
    while (retry < 2) {
      this.log(`分段${segInfo.index + 1}/${segInfo.size}[${retry}]`);

      const { text, hasDegradation } = await this.translatePrompt(
        prompt,
        maxNewToken,
        retry === 0 ? {} : { frequency_penalty: 0.2 }
      );
      const splitText = text.split('\n');

      if (!hasDegradation && seg.length === splitText.length) {
        return splitText;
      } else {
        retry += 1;
      }
    }

    // 进入逐句翻译模式
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
      timeout: 60_000_000, // 10 minutes
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

const makePrompt = (textToTranslate: string) =>
  `<reserved_106>将下面的日文文本翻译成中文：${textToTranslate}<reserved_107>`;

interface SakuraResultChunk {
  results: Array<{
    new_token: number;
    text: string;
  }>;
}
