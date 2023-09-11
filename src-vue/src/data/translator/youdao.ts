import ky from 'ky';

import {
  Glossary,
  Translator,
  createLengthSegmenterWrapper,
  createNonAiGlossaryWrapper,
  emptyLineFilterWrapper,
} from './base';

export class YoudaoTranslator implements Translator {
  log: (message: string) => void;
  glossaryWarpper: ReturnType<typeof createNonAiGlossaryWrapper>;
  segmentWarpper: ReturnType<typeof createLengthSegmenterWrapper>;

  constructor(log: (message: string) => void, glossary: Glossary) {
    this.log = log;
    this.glossaryWarpper = createNonAiGlossaryWrapper(glossary);
    this.segmentWarpper = createLengthSegmenterWrapper(2000);
  }

  translate = async (input: string[]) => {
    if (input.length === 0) return [];
    return emptyLineFilterWrapper(input, (input) =>
      this.glossaryWarpper(input, (input) =>
        this.segmentWarpper(input, (seg, _segInfo) =>
          this.translateSegment(seg)
        )
      )
    );
  };

  private key = 'fsdsogkndfokasodnaso';
  async init() {
    try {
      await ky.get('https://rlogs.youdao.com/rlog.php', {
        searchParams: {
          _npid: 'fanyiweb',
          _ncat: 'pageview',
          _ncoo: (2147483647 * Math.random()).toString(),
          _nssn: 'NULL',
          _nver: '1.2.0',
          _ntms: Date.now().toString(),
        },
        credentials: 'include',
      });

      const json: any = await ky
        .get('https://dict.youdao.com/webtranslate/key', {
          searchParams: {
            keyid: 'webfanyi-key-getter',
            ...(await getBaseBody('asdjnjfenknafdfsdfsd')),
          },
          credentials: 'include',
        })
        .json();

      this.key = json['data']['secretKey'];
    } catch (e) {
      this.log('无法获得Key，使用默认值');
    }
    return this;
  }

  async translateSegment(textsSrc: string[]): Promise<string[]> {
    const form = {
      i: textsSrc.join('\n'),
      from: 'ja',
      to: 'zh-CHS',
      dictResult: true,
      keyid: 'webfanyi',
      ...(await getBaseBody(this.key)),
    };
    const searchParams = new URLSearchParams();
    for (const name in form) {
      searchParams.append(name, (form as any)[name].toString());
    }

    const text = await ky
      .post('https://dict.youdao.com/webtranslate', {
        body: searchParams,
        credentials: 'include',
        headers: {
          Accept: 'application/json, text/plain, */*',
        },
      })
      .text();

    const decoded = await decode(text);
    try {
      const obj = JSON.parse(decoded);
      const result = obj['translateResult'].map((it: any) =>
        it.map((it: any) => it.tgt.trimEnd()).join('')
      );
      return result;
    } catch (e: any) {
      this.log(`　解码错误：${decoded}`);
      this.log('　目前有道翻译在部分机子上有问题，原因尚不清楚');
      this.log('　不过你可以选择手动修改cookie来解决，具体方法参考插件教程');
      throw 'quit';
    }
  }
}

async function getBaseBody(key: string) {
  const c = 'fanyideskweb';
  const p = 'webfanyi';
  const t = Date.now().toString();

  const MD5 = (await import('crypto-js/md5')).default;
  const sign = MD5(
    `client=${c}&mysticTime=${t}&product=${p}&key=${key}`
  ).toString();
  return {
    sign,
    client: c,
    product: p,
    appVersion: '1.0.0',
    vendor: 'web',
    pointParam: 'client,mysticTime,product',
    mysticTime: t,
    keyfrom: 'fanyi.web',
  };
}

async function decode(src: string) {
  const AES = (await import('crypto-js/aes')).default;
  const MD5 = (await import('crypto-js/md5')).default;
  const Utf8 = (await import('crypto-js/enc-utf8')).default;

  const key = MD5(
    'ydsecret://query/key/B*RGygVywfNBwpmBaZg*WT7SIOUP2T0C9WHMZN39j^DAdaZhAnxvGcCY6VYFwnHl'
  );
  const iv = MD5(
    'ydsecret://query/iv/C@lZe2YzHtZ2CYgaXKSVfsb7Y4QWHjITPPZ0nQp87fBeJ!Iv6v^6fvi2WN@bYpJ4'
  );
  const dec = AES.decrypt(src.replace(/_/g, '/').replace(/-/g, '+'), key, {
    iv,
  }).toString(Utf8);
  return dec;
}
