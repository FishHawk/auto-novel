import ky from 'ky';

export abstract class Translator {
  from_lang: string;
  to_lang: string;

  constructor(from_lang: string, to_lang: string) {
    this.from_lang = from_lang;
    this.to_lang = to_lang;
  }

  abstract translate(query_list: string[]): Promise<string[]>;
}