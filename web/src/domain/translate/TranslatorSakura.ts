import { Locator } from '@/data';
import { Glossary } from '@/model/Glossary';

import {
  Logger,
  SegmentContext,
  SegmentTranslator,
  createLengthSegmentor,
} from './Common';

export class SakuraTranslator implements SegmentTranslator {
  id = <const>'sakura';
  log: (message: string, detail?: string[]) => void;
  private api;
  version: string = '0.9';
  model?: string;
  fingerprint?: number[];
  segmentor = createLengthSegmentor(500);
  segLength = 500;
  prevSegLength = 500;

  constructor(
    log: Logger,
    { endpoint, segLength, prevSegLength }: SakuraTranslator.Config,
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
    const { model, fingerprint } = await this.detectModel();
    if (model !== undefined) {
      if (model.includes('0.8')) this.version = '0.8';
      else if (model.includes('0.9')) this.version = '0.9';
      else if (model.includes('0.10')) this.version = '0.10';
      else if (model.includes('1.0')) this.version = '1.0';
    }
    this.model = model;
    this.fingerprint = fingerprint;
    console.log('模型/指纹:');
    console.log(this.model);
    console.log(this.fingerprint);
    return this;
  }

  allowUpload = () => {
    if (this.segLength !== 500) {
      this.log('分段长度不是500');
      return false;
    }
    if (this.prevSegLength !== 500) {
      this.log('前文长度不是500');
      return false;
    }

    if (this.model === undefined) {
      this.log('无法确定模型名称');
      return false;
    }

    const isModelAllowed = SakuraTranslator.allowModels
      .map((it) => it.model)
      .some((it) => it === this.model || it === this.model + '.gguf');
    if (isModelAllowed) {
      this.log(`模型为${this.model}，允许上传`);
    } else {
      this.log(`模型为${this.model}，禁止上传`);
      return false;
    }

    if (this.fingerprint === undefined) {
      this.log('无法确定模型指纹');
      return false;
    }

    const fingerprint = this.fingerprint;
    const fingerprintKnownList = SakuraTranslator.allowModels
      .map((it) => it.fingerprint)
      .flat();
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
      console.log(`距离:${distance}`);
      return distance;
    });

    const isFingerprintLegal = distanceList.some((it) => it < 0.001);
    if (!isFingerprintLegal) {
      this.log('指纹检查未通过，请找站长反馈');
      return false;
    } else {
      this.log('指纹检查通过');
      return true;
    }
  };

  async translate(
    seg: string[],
    { glossary, prevSegs, signal }: SegmentContext,
  ): Promise<string[]> {
    const concatedSeg = seg.join('\n');
    const prevSegCount = -Math.ceil(this.prevSegLength / this.segLength);

    const concatedPrevSeg =
      prevSegCount === 0 ? '' : prevSegs.slice(prevSegCount).flat().join('\n');

    // 正常翻译
    let retry = 1;
    while (retry < 3) {
      const { text, hasDegradation } = await this.createChatCompletions(
        concatedSeg,
        glossary,
        concatedPrevSeg,
        signal,
        retry > 1,
      );
      const splitText = text.replaceAll('<|im_end|>', '').split('\n');

      const parts: string[] = [`第${retry}次`];
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
      } else {
        retry += 1;
      }
    }

    // 逐行翻译
    {
      this.log('逐行翻译');
      let degradationLineCount = 0;
      const resultPerLine = [];
      for (const line of seg) {
        const { text, hasDegradation } = await this.createChatCompletions(
          line,
          glossary,
          [concatedPrevSeg, ...resultPerLine].join('\n'),
          signal,
          true,
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
      return {};
    }

    const model = completion.model;
    if (
      completion.completion_probabilities === undefined ||
      completion.completion_probabilities.length === 0
    ) {
      return { model };
    }

    const fingerprint = completion.completion_probabilities[0].probs.map(
      (it) => it.prob,
    );

    return { model, fingerprint };
  }

  private async createChatCompletions(
    text: string,
    glossary: Glossary,
    prevText: string,
    signal?: AbortSignal,
    hasDegradation?: boolean,
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
      String.fromCharCode(ch.charCodeAt(0) - 0xfee0),
    );

    if (this.version === '0.10' || this.version === '1.0') {
      system(
        '你是一个轻小说翻译模型，可以流畅通顺地使用给定的术语表以日本轻小说的风格将日文翻译成简体中文，并联系上下文正确使用人称代词，注意不要混淆使役态和被动态的主语和宾语，不要擅自添加原文中没有的代词，也不要擅自增加或减少换行。',
      );
      if (prevText !== '') {
        assistant(prevText);
      }

      const glossaryHint = Object.entries(glossary)
        .map(([wordJp, wordZh]) => `${wordJp}->${wordZh}`)
        .join('\n');

      user(
        `根据以下术语表（可以为空）：\n${glossaryHint}\n\n将下面的日文文本根据上述术语表的对应关系和备注翻译成中文：${text}`,
      );
    } else {
      system(
        '你是一个轻小说翻译模型，可以流畅通顺地以日本轻小说的风格将日文翻译成简体中文，并联系上下文正确使用人称代词，不擅自添加原文中没有的代词。',
      );
      if (prevText !== '') {
        assistant(prevText);
      }

      // 替换术语表词汇
      for (const wordJp of Object.keys(glossary).sort(
        (a, b) => b.length - a.length,
      )) {
        const wordZh = glossary[wordJp];
        text = text.replaceAll(wordJp, wordZh);
      }

      user(`将下面的日文文本翻译成中文：${text}`);
    }

    const maxNewToken = Math.max(Math.ceil(text.length * 1.7), 100);
    const completion = await this.api.createChatCompletions(
      {
        model: '',
        messages,
        temperature: 0.1,
        top_p: 0.3,
        max_tokens: maxNewToken,
        frequency_penalty: hasDegradation ? 0.2 : 0.0,
      },
      {
        signal,
        timeout: false,
      },
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

  const model = (repo: string, model: string, fingerprint: number[][]) => ({
    repo,
    model,
    fingerprint,
  });

  export const allowModels = [
    model(
      'SakuraLLM/Sakura-32B-Qwen2beta-v0.9-GGUF',
      'sakura-32b-qwen2beta-v0.9-iq4xs.gguf',
      [
        [
          0.637474775314331, 0.2745130658149719, 0.03205988556146622,
          0.013636616989970207, 0.009562249295413494, 0.0072726053185760975,
          0.007086690980941057, 0.007071454077959061, 0.006449921987950802,
          0.00487272534519434,
        ],
        [
          0.612946629524231, 0.29618123173713684, 0.033997636288404465,
          0.013719908893108368, 0.010187946259975433, 0.0077759381383657455,
          0.007326382678002119, 0.006830626633018255, 0.0067310151644051075,
          0.004302578046917915,
        ],
        [
          0.5563070774078369, 0.298783540725708, 0.03193163499236107,
          0.026846086606383324, 0.022715333849191666, 0.016506964340806007,
          0.014548671431839466, 0.011763382703065872, 0.010536867193877697,
          0.010060448199510574,
        ],
      ],
    ),
    model(
      'SakuraLLM/Sakura-32B-Qwen2beta-v0.9.1-GGUF',
      'sakura-32b-qwen2beta-v0.9.1-iq4xs.gguf',
      [
        [
          0.5895127058029175, 0.3742421269416809, 0.008571512065827847,
          0.005501568783074617, 0.0046662273816764355, 0.004656270146369934,
          0.003773320000618696, 0.0034761286806315184, 0.0031783662270754576,
          0.002421919722110033,
        ],
        [
          0.49625661969184875, 0.4707738757133484, 0.008472394198179245,
          0.00468214089050889, 0.0041005234234035015, 0.0035940087400376797,
          0.0035077850334346294, 0.00345810828730464, 0.00293041137047112,
          0.002223958494141698,
        ],
        [
          0.5316017270088196, 0.4387364983558655, 0.0072052511386573315,
          0.0043828608468174934, 0.0035255481489002705, 0.0033778706565499306,
          0.003230227390304208, 0.00316042872145772, 0.0026986137963831425,
          0.0020809166599065065,
        ],
        [
          0.6628226041793823, 0.29875755310058594, 0.009163768030703068,
          0.0061043850146234035, 0.0049050115048885345, 0.0048668403178453445,
          0.004130433779209852, 0.003451106371358037, 0.0032930688466876745,
          0.0025052346754819155,
        ],
      ],
    ),
    model(
      'SakuraLLM/Sakura-14B-Qwen2beta-v0.9.1-GGUF',
      'sakura-14b-qwen2beta-v0.9.1-iq4xs.gguf',
      [
        [
          0.44965803623199463, 0.4034508168697357, 0.05095893144607544,
          0.023245327174663544, 0.017602942883968353, 0.014538204297423363,
          0.011166810058057308, 0.010693884454667568, 0.009813597425818443,
          0.008871445432305336,
        ],
      ],
    ),
    model(
      'SakuraLLM/Sakura-14B-Qwen2beta-v0.9.2-GGUF',
      'sakura-14b-qwen2beta-v0.9.2-iq4xs.gguf',
      [
        [
          0.5601178407669067, 0.10090667009353638, 0.07124997675418854,
          0.050760358572006226, 0.048443447798490524, 0.04311312735080719,
          0.034672778099775314, 0.03223879635334015, 0.03134223446249962,
          0.027154725044965744,
        ],
        // b2859
        [
          0.5544909238815308, 0.09134039282798767, 0.0702454224228859,
          0.055606868118047714, 0.05284511670470238, 0.04588409513235092,
          0.039813119918107986, 0.0325898602604866, 0.030937371775507927,
          0.026246793568134308,
        ],
        // https://books.fishhawk.top/forum/66601b3fe9f682238b4174a3
        [
          0.5889551043510437, 0.08219610154628754, 0.06368642300367355,
          0.05597573518753052, 0.04624505341053009, 0.044758766889572144,
          0.03576425090432167, 0.030524807050824165, 0.029894692823290825,
          0.021998988464474678,
        ],
      ],
    ),
    model(
      'SakuraLLM/Sakura-14B-Qwen2beta-v0.9.2-GGUF',
      'sakura-14b-qwen2beta-v0.9.2-q4km.gguf',
      [
        [
          0.6689561605453491, 0.07981256395578384, 0.052107073366642,
          0.04577327147126198, 0.04422539845108986, 0.030705934390425682,
          0.020494865253567696, 0.020072637125849724, 0.019512630999088287,
          0.018339477479457855,
        ],
      ],
    ),
  ];
}
