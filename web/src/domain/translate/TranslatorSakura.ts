import { Locator } from '@/data';

import {
  Logger,
  SegmentContext,
  SegmentTranslator,
  createLengthSegmentor,
} from './Common';
import { Glossary } from '@/model/Glossary';

export class SakuraTranslator implements SegmentTranslator {
  id = <const>'sakura';
  log: (message: string, detail?: string[]) => void;
  private api;
  version: string = '0.8';
  fingerprint?: number[];
  segmentor = createLengthSegmentor(500);
  segLength = 500;
  prevSegLength = 1000;

  constructor(
    log: Logger,
    { endpoint, segLength, prevSegLength }: SakuraTranslator.Config
  ) {
    this.log = log;
    this.api = Locator.openAiRepositoryFactory(endpoint, 'no-key');
    if (segLength !== undefined) {
      this.segmentor = createLengthSegmentor(segLength);
      this.segLength = segLength;
    }
    if (prevSegLength !== undefined) {
      this.prevSegLength = prevSegLength;
    }
  }

  async init() {
    const { version, fingerprint } = await this.detectModel();
    this.version = version;
    this.fingerprint = fingerprint;
    console.log('模型指纹');
    console.log(this.fingerprint);
    return this;
  }

  allowUpload = () => {
    if (this.segLength !== 500 || this.prevSegLength !== 1000) {
      return false;
    }
    if (this.fingerprint === undefined) {
      return false;
    } else {
      const fingerprint = this.fingerprint;
      const fingerprintKnownList = [
        // V9Q4
        [
          0.43284669518470764, 0.4130252003669739, 0.04670359566807747,
          0.026198342442512512, 0.018005838617682457, 0.017726685851812363,
          0.013591521419584751, 0.0112677663564682, 0.010921093635261059,
          0.009713421575725079,
        ],
        // V9bQ4
        [
          0.4376196265220642, 0.40473198890686035, 0.046124767512083054,
          0.042326465249061584, 0.014513801783323288, 0.013741392642259598,
          0.012511679902672768, 0.010453899390995502, 0.009444311261177063,
          0.008532224223017693,
        ],
        // V9Q5
        [
          0.47926297783851624, 0.3850986361503601, 0.048578787595033646,
          0.025202343240380287, 0.015049006789922714, 0.011538472957909107,
          0.010755039751529694, 0.009199273772537708, 0.007807321380823851,
          0.007508228067308664,
        ],
        // V9Q8
        [
          0.5050740242004395, 0.3581511378288269, 0.047719020396471024,
          0.023994628340005875, 0.01783129572868347, 0.010319961234927177,
          0.010319961234927177, 0.009544404223561287, 0.008622650057077408,
          0.008422906510531902,
        ],
        // qwen2beta v0.9 Q6_K
        [
          0.5330696105957031, 0.3233230710029602, 0.03381280228495598,
          0.024354450404644012, 0.01809868961572647, 0.01673855260014534,
          0.01420582365244627, 0.012934550642967224, 0.011777041479945183,
          0.011685391888022423,
        ],
        // qwen2beta v0.9 IQ4_XS
        [
          0.5346031188964844, 0.3217298090457916, 0.031361691653728485,
          0.022065704688429832, 0.020728811621665955, 0.016526449471712112,
          0.016017984598875046, 0.014246690087020397, 0.012377738952636719,
          0.010341987945139408,
        ],
        [
          0.5695551633834839, 0.26602110266685486, 0.037017278373241425,
          0.023593876510858536, 0.019706768915057182, 0.018498677760362625,
          0.01830877549946308, 0.018086310476064682, 0.01600310392677784,
          0.013208990916609764,
        ],
        [
          0.5865097045898438, 0.2559467554092407, 0.03481026366353035,
          0.022377753630280495, 0.021618124097585678, 0.017949266359210014,
          0.01727847382426262, 0.01687248796224594, 0.013742952607572079,
          0.012894189916551113,
        ],

        // qwen2beta v0.9 IQ4_XS rocm
        [
          0.5755994319915771, 0.29531994462013245, 0.027012469246983528,
          0.020408252254128456, 0.019373252987861633, 0.013949254527688026,
          0.01368272677063942, 0.013679255731403828, 0.011674472130835056,
          0.009300841949880123,
        ],

        // sakura-32b-qwen2beta-v0.9-iq4xs.gguf
        [
          0.637474775314331, 0.2745130658149719, 0.03205988556146622,
          0.013636616989970207, 0.009562249295413494, 0.0072726053185760975,
          0.007086690980941057, 0.007071454077959061, 0.006449921987950802,
          0.00487272534519434,
        ],
      ];

      const calculateDistance = (a: number[], b: number[]) => {
        let d = 0;
        for (let i = 0; i < a.length; i++) {
          const numA = a[i];
          const numB = b[i] ?? 0;
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
    { glossary, prevSegs, signal }: SegmentContext
  ): Promise<string[]> {
    const concatedSeg = seg.join('\n');
    const prevSegCount = -Math.ceil(this.prevSegLength / this.segLength);

    const concatedPrevSeg =
      prevSegCount === 0 ? '' : prevSegs.slice(prevSegCount).flat().join('\n');

    // 正常翻译
    {
      const { text, hasDegradation } = await this.createChatCompletions(
        concatedSeg,
        glossary,
        concatedPrevSeg,
        signal
      );
      const splitText = text.replaceAll('<|im_end|>', '').split('\n');

      const parts: string[] = [`第1次`];
      const linesNotMatched = seg.length !== splitText.length;
      if (hasDegradation) {
        parts.push('退化');
      } else if (linesNotMatched) {
        parts.push('行数不匹配');
      } else {
        parts.push('成功');
      }
      const detail = [seg.join('\n'), text];
      this.log(parts.join('　'), detail);

      if (!hasDegradation && !linesNotMatched) {
        return splitText;
      }
    }

    // 逐行翻译
    {
      this.log('第2次　逐行翻译');
      let degradationLineCount = 0;
      const resultPerLine = [];
      for (const line of seg) {
        const { text, hasDegradation } = await this.createChatCompletions(
          line,
          glossary,
          [concatedPrevSeg, ...resultPerLine].join('\n'),
          signal
        );
        if (hasDegradation) {
          degradationLineCount += 1;
          this.log(`单行退化${degradationLineCount}次`, [line, text]);
          if (degradationLineCount >= 2) {
            throw Error('单个分段有2行退化，Sakura翻译器可能存在异常');
          } else {
            resultPerLine.push(line);
          }
        } else {
          resultPerLine.push(text.replaceAll('<|im_end|>', ''));
        }
      }
      return resultPerLine;
    }
  }

  private async detectModel() {
    const text = '国境の長いトンネルを抜けると雪国であった';
    const completion = await this.api
      .llamacppCheck({
        prompt: `<|im_start|>system\n你是一个轻小说翻译模型，可以流畅通顺地以日本轻小说的风格将日文翻译成简体中文 ，并联系上下文正确使用人称代词，不擅自添加原文中没有的代词。<|im_end|>\n<|im_start|>user\n将下面的日文文本翻译成中文：${text}<|im_end|>\n<|im_start|>assistant\n`,
        temperature: 1,
        top_p: 1,
        n_predict: 1,
        n_probs: 10,
        min_keep: 10,
        seed: 0,
      })
      .catch(() => undefined);

    if (completion === undefined) {
      return { version: '0.9' };
    }

    let version = '0.8';
    if (completion.model.includes('0.9')) version = '0.9';
    if (completion.model.includes('0.10')) version = '0.10';

    const allow = [
      '0.9-Q4',
      '0.9-Q5',
      '0.9-Q6',
      '0.9-Q8',
      '0.9b-Q4',
      '0.9b-Q5',
      '0.9b-Q6',
      '0.9b-Q8',
      '0.9-iq4',
      '0.9-IQ4',
    ].some((it) => completion.model.includes(it));

    if (
      completion.completion_probabilities === undefined ||
      completion.completion_probabilities.length === 0 ||
      version !== '0.9' ||
      !allow
    ) {
      return { version };
    }

    const fingerprint = completion.completion_probabilities[0].probs.map(
      (it) => it.prob
    );

    return { version, fingerprint };
  }

  private async createChatCompletions(
    text: string,
    glossary: Glossary,
    prevText: string,
    signal?: AbortSignal
  ) {
    const messages: {
      role: 'system' | 'user' | 'assistant';
      content: string;
    }[] = [];

    const system = (content: string) => {
      messages.push({ role: 'system', content });
    };
    const user = (content: string) => {
      messages.push({ role: 'user', content });
    };
    const assistant = (content: string) => {
      messages.push({ role: 'assistant', content });
    };

    // 全角数字转换成半角数字
    text = text.replace(/[\uff10-\uff19]/g, (ch) =>
      String.fromCharCode(ch.charCodeAt(0) - 0xfee0)
    );

    if (this.version === '0.10') {
      system(
        '你是一个轻小说翻译模型，可以流畅通顺地使用给定的术语表以日本轻小说的风格将日文翻译成简体中文，并联系上下文正确使用人称代词，注意不要混淆使役态和被动态的主语和宾语，不要擅自添加原文中没有的代词，也不要擅自增加或减少换行。'
      );
      if (prevText !== '') {
        assistant(prevText);
      }

      const glossaryHint = Object.entries(glossary)
        .map(([wordJp, wordZh]) => `${wordJp}->${wordZh}`)
        .join('\n');

      user(
        `根据以下术语表（可以为空）：\n${glossaryHint}\n\n将下面的日文文本根据上述术语表的对应关系和备注翻译成中文：${text}`
      );
    } else {
      system(
        '你是一个轻小说翻译模型，可以流畅通顺地以日本轻小说的风格将日文翻译成简体中文，并联系上下文正确使用人称代词，不擅自添加原文中没有的代词。'
      );
      if (prevText !== '') {
        assistant(prevText);
      }

      // 替换术语表词汇
      for (const wordJp of Object.keys(glossary).sort(
        (a, b) => b.length - a.length
      )) {
        const wordZh = glossary[wordJp];
        text = text.replaceAll(wordJp, wordZh);
      }

      user(`将下面的日文文本翻译成中文：${text}`);
    }

    const maxNewToken = Math.ceil(text.length * 1.7);
    const completion = await this.api.createChatCompletions(
      {
        model: '',
        messages,
        temperature: 0.1,
        top_p: 0.3,
        max_tokens: maxNewToken,
        frequency_penalty: 0.2,
      },
      {
        signal,
        timeout: false,
      }
    );

    return {
      text: completion.choices[0].message.content!!,
      hasDegradation: completion.usage.completion_tokens >= maxNewToken,
    };
  }
}

export namespace SakuraTranslator {
  export interface Config {
    endpoint: string;
    segLength?: number;
    prevSegLength?: number;
  }
  export const create = (log: Logger, config: Config) =>
    new SakuraTranslator(log, config).init();
}
