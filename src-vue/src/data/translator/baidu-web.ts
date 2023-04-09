import ky from 'ky';
import { BaiduBaseTranslator } from './baidu';
import { Glossary } from './base';

function a(r: number, o: string) {
  for (let t = 0; t < o.length - 2; t += 3) {
    const a = o.charAt(t + 2);
    let aa = a >= 'a' ? a.charCodeAt(0) - 87 : Number(a);
    aa = '+' === o.charAt(t + 1) ? r >>> aa : r << aa;
    r = '+' === o.charAt(t) ? (r + aa) & 4294967295 : r ^ aa;
  }
  return r;
}
var token = function (r: string, gtk: string) {
  if (r.length > 30) {
    r =
      '' +
      r.substring(0, 10) +
      r.substring(Math.floor(r.length / 2) - 5, 10) +
      r.substring(r.length, r.length - 10);
  }

  const gtkList = gtk.split('.');
  const gtk1 = Number(gtkList[0]) || 0;
  const gtk2 = Number(gtkList[1]) || 0;

  let d = [];
  let j = 0;
  for (let i = 0; i < r.length; i++) {
    var m = r.charCodeAt(i);
    if (128 > m) {
      d[j++] = m;
    } else if (2048 > m) {
      d[j++] = (m >> 6) | 192;
    } else {
      if (
        55296 === (64512 & m) &&
        i + 1 < r.length &&
        56320 === (64512 & r.charCodeAt(i + 1))
      ) {
        m = 65536 + ((1023 & m) << 10) + (1023 & r.charCodeAt(++i));
        d[j++] = (m >> 18) | 240;
        d[j++] = ((m >> 12) & 63) | 128;
      } else {
        d[j++] = (m >> 12) | 224;
        d[j++] = ((m >> 6) & 63) | 128;
      }
      d[j++] = (63 & m) | 128;
    }
  }

  let S = gtk1;
  for (let s = 0; s < d.length; s++) {
    S = a(S + d[s], '+-a^+6');
  }
  S = a(S, '+-3^+b+-f');
  S ^= gtk2;
  0 > S && (S = (2147483647 & S) + 2147483648);
  S %= 1e6;
  return S.toString() + '.' + (S ^ gtk1);
};

export class BaiduWebTranslator extends BaiduBaseTranslator {
  private token = '';
  private gtk = '';

  static async createInstance(
    langSrc: string,
    langDst: string,
    glossary: Glossary
  ) {
    const translator = new this(langSrc, langDst, glossary);
    await translator.loadMainPage();
    await translator.loadMainPage();
    return translator;
  }

  private async loadMainPage() {
    const html = await ky
      .get('https://fanyi.baidu.com', { credentials: 'include' })
      .text();
    this.token = html.match(/token: '(.*?)',/)!![1];
    this.gtk = html.match(/window.gtk = "(.*?)";/)!![1];
  }

  async translateInner(textSrc: string): Promise<string[]> {
    const url = 'https://fanyi.baidu.com/v2transapi';
    const sign = token(textSrc, this.gtk);
    const data = {
      from: this.langSrc,
      to: this.langDst,
      query: textSrc,
      simple_means_flag: 3,
      sign: sign,
      token: this.token,
      domain: 'common',
    };

    const json: any = await ky
      .post(url, { json: data, credentials: 'include' })
      .json();
    if ('error' in json) {
      throw Error(`Baidu translator error ${json.error}: ${json.msg}`);
    } else if ('errno' in json) {
      if (json.errno == 1000) {
        throw Error(
          `Baidu translator error ${json.errno}: ${json.errmsg}，可能是因为输入为空`
        );
      } else {
        throw Error(`Baidu translator error ${json.errno}: ${json.errmsg}`);
      }
    } else {
      return json.trans_result.data.map((item: any) => item.dst);
    }
  }
}
