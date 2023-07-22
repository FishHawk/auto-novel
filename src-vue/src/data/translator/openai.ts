import ky from 'ky';
import { v4 as uuidv4 } from 'uuid';
import { get_encoding } from 'tiktoken';

import { Translator } from './adapter';
import { Segmenter, TokenSegmenter } from './util';

function detectChinese(text: string) {
  const re =
    /[:|#| |0-9|\u4e00-\u9fa5|\u3002|\uff1f|\uff01|\uff0c|\u3001|\uff1b|\uff1a|\u201c|\u201d|\u2018|\u2019|\uff08|\uff09|\u300a|\u300b|\u3008|\u3009|\u3010|\u3011|\u300e|\u300f|\u300c|\u300d|\ufe43|\ufe44|\u3014|\u3015|\u2026|\u2014|\uff5e|\ufe4f|\uffe5]/;
  let count = 0;
  for (const c of text) {
    if (re.test(c)) {
      count++;
    }
  }
  return count / text.length;
}

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
      throw Error(`Internal Server Error: ${line}`);
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
  private log: (message: string) => void;
  private accessToken: string;
  private promptTemplate =
    '请你作为一个轻小说翻译者，将下面的日文轻小说片段翻译成简体中文。要求翻译准确，译文流畅。要求人名和专有名词也要翻译成中文。既不要漏掉任何一句，也不要增加额外的说明。注意保持换行格式，译文的行数必须要和原文相等。';

  static async create(accessToken: string, log?: (message: string) => void) {
    accessToken = accessToken.trim();
    try {
      const obj = JSON.parse(accessToken);
      accessToken = obj.accessToken;
    } catch {}
    const translator = new this(accessToken, log);
    return translator;
  }

  constructor(accessToken: string, log?: (message: string) => void) {
    super();
    this.accessToken = accessToken;
    if (log) {
      this.log = log;
    } else {
      this.log = console.log;
    }
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
        .map((s, i) => s.replace(`#${i}:`, '').replace(`#${i}：`, ''));
      const outputChinesePercentage = detectChinese(outputRaw);
      const encoder = get_encoding('p50k_base');
      this.log(
        [
          `分段`,
          `中文占比:${outputChinesePercentage.toFixed(2)}`,
          `长度:${encoder.encode(prompt).length}`,
          `原文行数:${input.length}`,
          `翻译行数:${output.length}`,
        ].join('　')
      );
      encoder.free();

      if (input.length !== output.length) {
        retry += 1;
        if (retry >= 3) {
          throw Error('重试次数太多');
        }
        this.log(`重试${retry}：输出行数不匹配`);
        this.log(`\nGPT输入：${prompt}`);
        this.log(`\nGPT输出：${outputRaw}`);
      } else if (outputChinesePercentage < 0.75) {
        retry += 1;
        if (retry >= 3) {
          throw Error('重试次数太多');
        }
        this.log(`重试${retry}：输出语言中文占比小于0.75`);
        this.log(`\nGPT输入：${prompt}`);
        this.log(`\nGPT输出：${outputRaw}`);
      } else {
        return output;
      }
    }
  }
}
