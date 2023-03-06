import { assert } from '@vueuse/shared';
import { Translator } from './base';

export class BaiduQueryProcessor {
  splitedTexts: string[];
  splitedIndex: number[];
  filteredSplitedTexts: string[];

  constructor(texts: string[]) {
    this.splitedTexts = [];
    this.splitedIndex = [];
    for (const text of texts) {
      const splited = text.split('\n');
      this.splitedTexts = this.splitedTexts.concat(splited);
      this.splitedIndex.push(splited.length);
    }
    this.filteredSplitedTexts = this.splitedTexts.filter(
      (q) => q.trim() !== ''
    );
  }

  getPreProcessed(): string[] {
    return this.filteredSplitedTexts;
  }

  private *concat(textDst: string[]) {
    assert(textDst.length == this.splitedTexts.length);
    const recoveredTextDst: string[] = [];
    for (const result of textDst) {
      recoveredTextDst.push(result);
      if (recoveredTextDst.length == this.splitedIndex[0]) {
        this.splitedIndex.shift();
        yield recoveredTextDst.join('\n');
        recoveredTextDst.length = 0;
      }
    }
  }

  applyPostProcess(texts: string[]): string[] {
    assert(texts.length == this.filteredSplitedTexts.length);
    const insertedTexts: string[] = [];
    for (const textSrc of this.splitedTexts) {
      if (textSrc.trim() !== '') {
        insertedTexts.push(texts.shift()!);
      } else {
        insertedTexts.push(textSrc);
      }
    }
    return Array.from(this.concat(insertedTexts));
  }
}

export abstract class BaiduBaseTranslator extends Translator {
  private limit_per_request = 2000;

  private *chunkQuery(query: string[]): Generator<string | string[]> {
    const chunked: string[] = [];
    let chunked_size = 0;
    for (const line of query) {
      const line_size = line.length;
      if (line_size + chunked_size <= this.limit_per_request) {
        chunked.push(line);
        chunked_size += line_size;
      } else {
        if (chunked.length >= 0) {
          yield chunked;
          chunked.length = 0;
          chunked_size = 0;
        }
        if (line_size <= this.limit_per_request) {
          chunked.push(line);
          chunked_size += line_size;
        } else {
          yield line;
        }
      }
    }
    if (chunked.length >= 0) {
      yield chunked;
    }
  }

  private chunkString(query: string): string[] {
    const chunks: string[] = [];
    for (let i = 0; i < query.length; i += this.limit_per_request) {
      chunks.push(query.slice(i, i + this.limit_per_request));
    }
    return chunks;
  }

  abstract translateInner(query: string): Promise<string[]>;

  async translate(textSrc: string[]): Promise<string[]> {
    const processor = new BaiduQueryProcessor(textSrc);
    const processedTextSrc = processor.getPreProcessed();

    let textDst: string[] = [];
    for (const chunkedTextSrc of this.chunkQuery(processedTextSrc)) {
      if (typeof chunkedTextSrc === 'string') {
        const chunkedTextDst = this.chunkString(chunkedTextSrc)
          .map(async (chunked_string) => {
            return await this.translateInner(chunked_string);
          })
          .flat()
          .join('');
        textDst.push(chunkedTextDst);
      } else {
        const chunkedTextDst = await this.translateInner(
          chunkedTextSrc.join('\n')
        );
        textDst = textDst.concat(chunkedTextDst);
      }
    }

    const recovered_result_list = processor.applyPostProcess(textDst);
    if (recovered_result_list.length != textSrc.length) {
      throw Error('Baidu translator error');
    }
    return recovered_result_list;
  }
}
