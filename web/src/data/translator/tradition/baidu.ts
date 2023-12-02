import { KyInstance } from 'ky/distribution/types/ky';

import { BaseTranslatorConfig, Glossary, SegmentTranslator } from '../type';
import { createGlossaryWrapper, createLengthSegmentor } from './common';

export type BaiduTranslatorConfig = BaseTranslatorConfig;

export class BaiduTranslator implements SegmentTranslator {
  private client: KyInstance;
  glossary: Glossary;
  log: (message: string) => void;

  private glossaryWarpper: ReturnType<typeof createGlossaryWrapper>;

  constructor({ client, glossary, log }: BaiduTranslatorConfig) {
    this.client = client.create({
      prefixUrl: 'https://fanyi.baidu.com',
      credentials: 'include',
    });
    this.glossary = glossary;
    this.log = log;

    this.glossaryWarpper = createGlossaryWrapper(glossary);
  }

  private token = '';
  private gtk = '';

  async init() {
    await this.loadMainPage();
    await this.loadMainPage();
    if (this.token === '') throw Error('无法获取token');
    if (this.gtk === '') throw Error('无法获取gtk');
    return this;
  }

  private async loadMainPage() {
    const html = await this.client.get('').text();

    const match = (pattern: RegExp) => {
      const res = html.match(pattern);
      if (res) return res[1];
      else return null;
    };

    this.token = match(/token: '(.*?)',/) ?? '';
    this.gtk =
      match(/window\.gtk = "(.*?)";/) ?? // Desktop
      match(/gtk: '(.*?)'/) ?? // Mobile
      '';
  }

  createSegments = createLengthSegmentor(3500);

  async translate(
    seg: string[],
    _segInfo: { index: number; size: number }
  ): Promise<string[]> {
    return this.glossaryWarpper(seg, (seg) => this.translateInner(seg));
  }

  async translateInner(input: string[]): Promise<string[]> {
    // 开头的空格似乎会导致998错误
    const newInput = input.slice();
    newInput[0] = newInput[0].trimStart();
    const query = newInput.join('\n');

    const json: any = await this.client
      .post('v2transapi', {
        body: new URLSearchParams({
          from: 'jp',
          to: 'zh',
          query,
          simple_means_flag: '3',
          sign: sign(query, this.gtk),
          token: this.token,
          domain: 'common',
        }),
      })
      .json();

    if ('error' in json) {
      throw Error(`百度翻译错误：${json.error}: ${json.msg}`);
    } else if ('errno' in json) {
      if (json.errno == 1000) {
        throw Error(
          `百度翻译错误：${json.errno}: ${json.errmsg}，可能是因为输入为空`
        );
      } else {
        throw Error(`百度翻译错误：${json.errno}: ${json.errmsg}`);
      }
    } else {
      return json.trans_result.data.map((item: any) => item.dst);
    }
  }
}

const sign = function (r: string, gtk: string) {
  r = b(r);

  const encodedCodes = [];
  for (let i = 0; i < r.length; i++) {
    let char = r.charCodeAt(i);
    if (char < 0x80) {
      encodedCodes.push(char);
    } else {
      if (char < 0x800) {
        encodedCodes.push((char >> 6) | 0xc0);
      } else if (
        0xd800 === (0xfc00 & char) &&
        i + 1 < r.length &&
        0xdc00 === (0xfc00 & r.charCodeAt(i + 1))
      ) {
        char = 0x10000 + ((1023 & 0x3ff) << 10) + (0x3ff & r.charCodeAt(++i));
        encodedCodes.push((char >> 18) | 0xf0);
        encodedCodes.push(((char >> 12) & 0x3f) | 0x80);
      } else {
        encodedCodes.push((char >> 12) | 0xe0);
        encodedCodes.push(((char >> 6) & 0x3f) | 0x80);
      }
      encodedCodes.push((63 & char) | 0x80);
    }
  }

  const gtkArray = gtk.split('.');
  const gtk1 = Number(gtkArray[0]) || 0;
  const gtk2 = Number(gtkArray[1]) || 0;

  let S = gtk1;
  const key1 = '+-a^+6';
  const key2 = '+-3^+b+-f';

  for (let s = 0; s < encodedCodes.length; s++) {
    S += encodedCodes[s];
    S = a(S, key1);
  }

  S = a(S, key2);

  S ^= gtk2;

  if (S < 0) {
    S = (2147483647 & S) + 2147483648;
  }

  S %= 1e6;

  return S.toString() + '.' + (S ^ gtk1);
};

function a(r: any, o: any) {
  for (var t = 0; t < o.length - 2; t += 3) {
    var a = o.charAt(t + 2);
    (a = a >= 'a' ? a.charCodeAt(0) - 87 : Number(a)),
      (a = '+' === o.charAt(t + 1) ? r >>> a : r << a),
      (r = '+' === o.charAt(t) ? (r + a) & 4294967295 : r ^ a);
  }
  return r;
}

function e(t: any, e?: any) {
  (null == e || e > t.length) && (e = t.length);
  for (var n = 0, r = new Array(e); n < e; n++) r[n] = t[n];
  return r;
}

function b(t: any) {
  var o,
    i = t.match(/[\uD800-\uDBFF][\uDC00-\uDFFF]/g);
  if (null === i) {
    var a = t.length;
    a > 30 &&
      (t = ''
        .concat(t.substr(0, 10))
        .concat(t.substr(Math.floor(a / 2) - 5, 10))
        .concat(t.substr(-10, 10)));
  } else {
    for (
      var s = t.split(/[\uD800-\uDBFF][\uDC00-\uDFFF]/),
        c = 0,
        u = s.length,
        l: any[] = [];
      c < u;
      c++
    )
      '' !== s[c] &&
        l.push.apply(
          l,
          (function (t) {
            if (Array.isArray(t)) return e(t);
          })((o = s[c].split(''))) ||
            (function (t) {
              if (
                ('undefined' != typeof Symbol && null != t[Symbol.iterator]) ||
                null != t['@@iterator']
              )
                return Array.from(t);
            })(o) ||
            (function (t, n) {
              if (t) {
                if ('string' == typeof t) return e(t, n);
                var r = Object.prototype.toString.call(t).slice(8, -1);
                return (
                  'Object' === r && t.constructor && (r = t.constructor.name),
                  'Map' === r || 'Set' === r
                    ? Array.from(t)
                    : 'Arguments' === r ||
                      /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(r)
                    ? e(t, n)
                    : void 0
                );
              }
            })(o) ||
            (function () {
              throw new TypeError(
                'Invalid attempt to spread non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method.'
              );
            })()
        ),
        c !== u - 1 && l.push(i[c]);
    var p = l.length;
    p > 30 &&
      (t =
        l.slice(0, 10).join('') +
        l.slice(Math.floor(p / 2) - 5, Math.floor(p / 2) + 5).join('') +
        l.slice(-10).join(''));
  }

  return t;
}
