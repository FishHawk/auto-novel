import ky from 'ky';
import CryptoJS from 'crypto-js';
import { Translator } from './adapter';

function getBaseBody(key: string) {
  const c = 'fanyideskweb';
  const p = 'webfanyi';
  const t = Date.now().toString();

  const sign = CryptoJS.MD5(
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

export class YoudaoTranslator implements Translator {
  static label = '有道';
  size = 2000;
  key = 'fsdsogkndfokasodnaso';

  static async create() {
    return await new this().init();
  }

  private async init() {
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
      });

      const json: any = await ky
        .get('https://dict.youdao.com/webtranslate/key', {
          searchParams: {
            keyid: 'webfanyi-key-getter',
            ...getBaseBody('asdjnjfenknafdfsdfsd'),
          },
        })
        .json();

      this.key = json['data']['secretKey'];
    } catch (e) {
      console.log('无法获得Key，使用默认值');
    }
    return this;
  }

  async translate(textsSrc: string[]): Promise<string[]> {
    const form = {
      i: textsSrc.join('\n'),
      from: 'ja',
      to: 'zh-CHS',
      dictResult: true,
      keyid: 'webfanyi',
      ...getBaseBody(this.key),
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

    const json = this.decode(text);
    const result = json['translateResult'].map((it: any) => {
      return it.map((it: any) => it.tgt.trimEnd()).join('');
    });
    return result;
  }

  private decode(src: string) {
    const key = CryptoJS.MD5(
      'ydsecret://query/key/B*RGygVywfNBwpmBaZg*WT7SIOUP2T0C9WHMZN39j^DAdaZhAnxvGcCY6VYFwnHl'
    );
    const iv = CryptoJS.MD5(
      'ydsecret://query/iv/C@lZe2YzHtZ2CYgaXKSVfsb7Y4QWHjITPPZ0nQp87fBeJ!Iv6v^6fvi2WN@bYpJ4'
    );
    const dec = CryptoJS.AES.decrypt(
      src.replace(/_/g, '/').replace(/-/g, '+'),
      key,
      { iv }
    ).toString(CryptoJS.enc.Utf8);
    const json = JSON.parse(dec);
    return json;
  }
}
