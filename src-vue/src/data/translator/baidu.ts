import { assert } from '@vueuse/shared';
import { Translator } from './base';

export class BaiduQueryProcessor {
  splited_query_list: string[];
  splited_index: number[];
  filtered_splited_query_list: string[];

  constructor(query_list: string[]) {
    this.splited_query_list = [];
    this.splited_index = [];
    for (const query of query_list) {
      const splited = query.split('\n');
      this.splited_query_list = this.splited_query_list.concat(splited);
      this.splited_index.push(splited.length);
    }
    this.filtered_splited_query_list = this.splited_query_list.filter(
      (q) => q.trim() !== ''
    );
  }

  get(): string[] {
    return this.filtered_splited_query_list;
  }

  private *concat(result_list: string[]) {
    assert(result_list.length == this.splited_query_list.length);
    const recovered: string[] = [];
    for (const result of result_list) {
      recovered.push(result);
      if (recovered.length == this.splited_index[0]) {
        this.splited_index.shift();
        yield recovered.join('\n');
        recovered.length = 0;
      }
    }
  }

  recover(result_list: string[]): string[] {
    assert(result_list.length == this.filtered_splited_query_list.length);
    const inserted_result_list: string[] = [];
    for (const query of this.splited_query_list) {
      if (query.trim() !== '') {
        inserted_result_list.push(result_list.shift()!);
      } else {
        inserted_result_list.push(query);
      }
    }
    return Array.from(this.concat(inserted_result_list));
  }
}

export abstract class BaiduBaseTranslator extends Translator {
  private limit_per_request = 2000;

  private *chunk_query(query: string[]): Generator<string | string[]> {
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

  private chunk_string(query: string): string[] {
    const chunks: string[] = [];
    for (let i = 0; i < query.length; i += this.limit_per_request) {
      chunks.push(query.slice(i, i + this.limit_per_request));
    }
    return chunks;
  }

  abstract inner_translate(query: string): Promise<string[]>;

  async translate(query_list: string[]): Promise<string[]> {
    const processor = new BaiduQueryProcessor(query_list);
    const processed_query_list = processor.get();
    let result_list: string[] = [];
    for (const chunked_query of this.chunk_query(processed_query_list)) {
      if (typeof chunked_query === 'string') {
        const chunked_translated = this.chunk_string(chunked_query)
          .map(async (chunked_string) => {
            return await this.inner_translate(chunked_string);
          })
          .flat()
          .join('');
        result_list.push(chunked_translated);
      } else {
        const chunked_translated = await this.inner_translate(
          chunked_query.join('\n')
        );
        result_list = result_list.concat(chunked_translated);
      }
    }

    const recovered_result_list = processor.recover(result_list);
    if (recovered_result_list.length != query_list.length) {
      throw Error('Baidu translator error');
    }
    return recovered_result_list;
  }
}
