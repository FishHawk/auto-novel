//TODO 未完成的有道翻译，似乎不可能在网页上运行，可能会迁移到浏览器插件上。
// import ky from 'ky';
// import md5 from 'js-md5';
// import aes from 'aes-js';

import { Glossary, Translator } from './base';

// function sign(t: string, key: string) {
//   return md5(`client=fanyideskweb&mysticTime=${t}&product=webfanyi&key=${key}`);
// }

// function getBaseBody(key: string) {
//   const t = Date.now().toString();
//   return {
//     sign: sign(t, key),
//     client: 'fanyideskweb',
//     product: 'webfanyi',
//     appVersion: '1.0.0',
//     vendor: 'web',
//     pointParam: 'client,mysticTime,product',
//     mysticTime: t,
//     keyfrom: 'fanyi.web',
//   };
// }

// export class YoudaoTranslator extends Translator {
//   key = '';

//   constructor(langSrc: string, langDst: string, glossary: Glossary) {
//     super(langSrc, langDst, glossary);
//   }

//   private static aesCbc = this.createAesCbc();
//   private static createAesCbc() {
//     const key =
//       'ydsecret://query/key/B*RGygVywfNBwpmBaZg*WT7SIOUP2T0C9WHMZN39j^DAdaZhAnxvGcCY6VYFwnHl';
//     const iv =
//       'ydsecret://query/iv/C@lZe2YzHtZ2CYgaXKSVfsb7Y4QWHjITPPZ0nQp87fBeJ!Iv6v^6fvi2WN@bYpJ4';
//     return new aes.ModeOfOperation.cbc(
//       md5.digest(key).slice(0, 16),
//       md5.digest(iv).slice(0, 16)
//     );
//   }

//   static async createInstance() {
//     return await new this('ja', 'zh-CHS', {}).init();
//   }

//   async init() {
//     await ky.get('https://rlogs.youdao.com/rlog.php', {
//       searchParams: {
//         _npid: 'fanyiweb',
//         _ncat: 'pageview',
//         _ncoo: (2147483647 * Math.random()).toString(),
//         _nssn: 'NULL',
//         _nver: '1.2.0',
//         _ntms: Date.now().toString(),
//       },
//       credentials: 'include',
//     });

//     const json: any = await ky
//       .get('https://dict.youdao.com/webtranslate/key', {
//         searchParams: {
//           keyid: 'webfanyi-key-getter',
//           ...getBaseBody('asdjnjfenknafdfsdfsd'),
//         },
//         credentials: 'include',
//       })
//       .json();
//     this.key = json['data']['secretKey'];
//     return this;
//   }

//   async translate(textsSrc: string[]): Promise<string[]> {
//     const form = {
//       i: textsSrc[0],
//       from: this.langSrc,
//       to: this.langDst,
//       dictResult: true,
//       keyid: 'webfanyi',
//       ...getBaseBody(this.key),
//     };
//     const searchParams = new URLSearchParams();
//     for (const name in form) {
//       searchParams.append(name, (form as any)[name].toString());
//     }

//     const text = await ky
//       .post('https://dict.youdao.com/webtranslate', {
//         body: searchParams,
//         credentials: 'include',
//       })
//       .text();

//     const json = this.decode(text);
//     return [];
//   }

//   decode(ciphertext: string) {
//     const encryptedBytes = Buffer.from(ciphertext, 'base64');
//     const decryptedBytes = YoudaoTranslator.aesCbc.decrypt(encryptedBytes);
//     const decryptedText = aes.utils.utf8.fromBytes(decryptedBytes);
//     const json = JSON.parse(decryptedText);
//     return json;
//   }
// }
