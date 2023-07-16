import ky from 'ky';
import { v4 as uuidv4 } from 'uuid';
import { franc } from 'franc-min';
import { getEncoding } from 'js-tiktoken';
import { get_encoding } from 'tiktoken';

import { Translator } from './adapter';
import { Segmenter, TokenSegmenter } from './util';

async function ask(accessToken: string, prompt: string) {
  const headers = {
    Accept: 'text/event-stream',
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json',
    'User-Agent':
      'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36',
  };
  const messages = [
    {
      id: uuidv4(),
      role: 'user',
      author: { role: 'user' },
      content: { content_type: 'text', parts: [prompt] },
    },
  ];
  const data = {
    action: 'next',
    messages: messages,
    parent_message_id: uuidv4(),
    model: 'text-davinci-002-render-sha',
    history_and_training_disabled: false,
  };

  const response = await ky
    .post('https://bypass.churchless.tech/conversation', {
      json: data,
      timeout: false,
      headers,
      credentials: 'include',
    })
    .text();

  let answer = '';
  for (let line of response.split('\n')) {
    if (line.toLowerCase() === 'internal server error') {
      console.log(`Internal Server Error: ${line}`);
      throw Error('Internal Server Error');
    } else if (!line) {
      continue;
    } else if (line.startsWith('data: ')) {
      line = line.slice(6);
    }
    if (line == '[DONE]') {
      break;
    }
    try {
      const obj = JSON.parse(line);
      if (obj.message.author.role !== 'assistant') {
        continue;
      }
      answer = obj.message?.content?.parts?.[0] ?? '';
    } catch {
      continue;
    }
  }
  return answer;
}

export class OpenAiTranslator extends Translator {
  private accessToken: string;
  private promptTemplate =
    '请你作为一个轻小说翻译者，将下面的日文轻小说片段翻译成简体中文。要求翻译准确，译文流畅。既不要漏掉任何一句，也不要增加额外的说明。注意保持换行格式，译文的行数必须要和原文相等。';

  static async create(accessToken: string) {
    accessToken = accessToken.trim();
    try {
      const obj = JSON.parse(accessToken);
      accessToken = obj.accessToken;
    } catch {}
    const translator = new this(accessToken);
    return translator;
  }

  constructor(accessToken: string) {
    super();
    this.accessToken = accessToken;
  }

  createSegmenter(input: string[]): Segmenter {
    return new TokenSegmenter(input, 1800, 40);
  }

  async translateSegment(input: string[]): Promise<string[]> {
    const prompt =
      this.promptTemplate +
      '小说原文如下：\n' +
      input.map((s, i) => `#${i}:${s}`).join('\n');

    let retry = 0;
    while (true) {
      const outputRaw = await ask(this.accessToken, prompt);
      const output = outputRaw
        .split('\n')
        .filter((s) => s.trim())
        .map((s, i) => s.replace(`#${i}:`, ''));
      const outputLang = franc(outputRaw);
      const encoder = get_encoding('p50k_base');
      console.log(
        [
          `语言:${outputLang}`,
          `长度:${encoder.encode(prompt).length}`,
          `原文行数:${input.length}`,
          `翻译行数:${output.length}`,
        ].join('  ')
      );
      encoder.free();

      if (input.length !== output.length) {
        retry += 1;
        if (retry >= 3) {
          throw Error('重试次数太多');
        }
        console.log(`重试${retry}:输出行数不匹配`);
      } else if (outputLang !== 'cmn') {
        retry += 1;
        if (retry >= 3) {
          throw Error('重试次数太多');
        }
        console.log(`重试${retry}:输出语言不是中文`);
      } else {
        return output;
      }
    }
  }
}
