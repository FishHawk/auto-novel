import ky from 'ky';

import {
  Glossary,
  Translator,
  createLengthSegmenterWrapper,
  createNonAiGlossaryWrapper,
  emptyLineFilterWrapper,
} from './base';

export class BaiduTranslator implements Translator {
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

  private token = '';
  private gtk = '';
  async init() {
    await this.loadMainPage();
    await this.loadMainPage();
    return this;
  }

  private async loadMainPage() {
    const html = await ky
      .get('https://fanyi.baidu.com', { credentials: 'include' })
      .text();
    this.token = html.match(/token: '(.*?)',/)!![1];
    this.gtk = html.match(/window.gtk = "(.*?)";/)!![1];
  }

  async translateSegment(input: string[]): Promise<string[]> {
    // 开头的空格似乎会导致998错误
    const newInput = input.slice();
    newInput[0] = newInput[0].trimStart();

    const query = newInput.join('\n');
    const sign = token(query, this.gtk);
    const data = {
      from: 'jp',
      to: 'zh',
      query: query,
      simple_means_flag: 3,
      sign: sign,
      token: this.token,
      domain: 'common',
    };
    const searchParams = new URLSearchParams();
    for (const name in data) {
      searchParams.append(name, (data as any)[name].toString());
    }

    const json: any = await ky
      .post('https://fanyi.baidu.com/v2transapi', {
        body: searchParams,
        credentials: 'include',
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

function a(r: any, o: any) {
  for (var t = 0; t < o.length - 2; t += 3) {
    var a = o.charAt(t + 2);
    (a = a >= 'a' ? a.charCodeAt(0) - 87 : Number(a)),
      (a = '+' === o.charAt(t + 1) ? r >>> a : r << a),
      (r = '+' === o.charAt(t) ? (r + a) & 4294967295 : r ^ a);
  }
  return r;
}
var C: any = null;
var token = function (r: any, _gtk: any) {
  var o = r.length;
  o > 30 &&
    (r =
      '' +
      r.substr(0, 10) +
      r.substr(Math.floor(o / 2) - 5, 10) +
      r.substring(r.length, r.length - 10));
  var t: any = void 0,
    t = null !== C ? C : (C = _gtk || '') || '';
  for (
    var e = t.split('.'),
      h = Number(e[0]) || 0,
      i = Number(e[1]) || 0,
      d = [],
      f = 0,
      g = 0;
    g < r.length;
    g++
  ) {
    var m = r.charCodeAt(g);
    128 > m
      ? (d[f++] = m)
      : (2048 > m
          ? (d[f++] = (m >> 6) | 192)
          : (55296 === (64512 & m) &&
            g + 1 < r.length &&
            56320 === (64512 & r.charCodeAt(g + 1))
              ? ((m = 65536 + ((1023 & m) << 10) + (1023 & r.charCodeAt(++g))),
                (d[f++] = (m >> 18) | 240),
                (d[f++] = ((m >> 12) & 63) | 128))
              : (d[f++] = (m >> 12) | 224),
            (d[f++] = ((m >> 6) & 63) | 128)),
        (d[f++] = (63 & m) | 128));
  }
  for (var S = h, u = '+-a^+6', l = '+-3^+b+-f', s = 0; s < d.length; s++)
    (S += d[s]), (S = a(S, u));
  return (
    (S = a(S, l)),
    (S ^= i),
    0 > S && (S = (2147483647 & S) + 2147483648),
    (S %= 1e6),
    S.toString() + '.' + (S ^ h)
  );
};
