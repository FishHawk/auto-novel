//OpenCC Lib掛了，先這樣湊合
import { mapper } from '@/util/web/keyword';
import { TSJDictionary } from '@/util/dictionary/tsj';

const convert = (query: string): string[] => {
  if (
    query === '' ||
    query === '|' ||
    query.includes('>') ||
    query.includes('<')
  )
    return [];

  const resultJ = query.replace(/./g, (char) => {
    return TSJDictionary.TtoJ[char] || char;
  });
  const resultS = query.replace(/./g, (char) => {
    return TSJDictionary.TtoS[char] || char;
  });
  return Array.from(new Set([query, resultJ, resultS]));
};
const processQuery = (input: string): string => {
  const queries = input.split(' ');
  const result: string[] = [];

  queryloop: for (const query of queries) {
    if (query.includes('$')) {
      for (const [_, cns, cnt] of mapper) {
        if (query.replace(/^-+|[-$]+$/g, '') == cnt) {
          result.push(query.replace(cnt, cns));
          continue queryloop;
        }
      }
      result.push(query);
    } else {
      const translated = convert(query);
      result.push(
        translated.length <= 0
          ? query
          : translated.length === 1
            ? translated[0]
            : `(${translated.join('|')})`,
      );
    }
  }
  return result.join(' ');
};

export const LanguageProcess = {
  processQuery,
};
