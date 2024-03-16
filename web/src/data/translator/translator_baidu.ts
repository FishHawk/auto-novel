import { Glossary } from '@/model/Glossary';

import { Baidu } from './api/baidu';
import { createGlossaryWrapper, createLengthSegmentor } from './common';
import { BaseTranslatorConfig, SegmentTranslator } from './type';

export type BaiduTranslatorConfig = BaseTranslatorConfig;

export class BaiduTranslator implements SegmentTranslator {
  log: (message: string) => void;
  private api = new Baidu();

  constructor({ log }: BaiduTranslatorConfig) {
    this.log = log;
  }

  async init() {
    await this.api.refreshGtkAndToken();
    await this.api.refreshGtkAndToken();
    if (this.api.token === '') throw Error('无法获取token');
    if (this.api.gtk === '') throw Error('无法获取gtk');
    return this;
  }

  createSegments = createLengthSegmentor(3500);

  async translate(
    seg: string[],
    _segInfo: { index: number; size: number },
    glossary: Glossary,
    signal?: AbortSignal
  ): Promise<string[]> {
    return createGlossaryWrapper(glossary)(seg, (seg) =>
      this.translateInner(seg, signal)
    );
  }

  async translateInner(
    input: string[],
    signal?: AbortSignal
  ): Promise<string[]> {
    // 开头的空格似乎会导致998错误
    const newInput = input.slice();
    newInput[0] = newInput[0].trimStart();
    const query = newInput.join('\n');

    const json = await this.api.v2transapi(query, { signal });

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
