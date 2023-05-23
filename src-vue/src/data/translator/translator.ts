import { Glossary, TranslatorAdapter } from './adapter';
import { BaiduTranslator } from './baidu';
import { YoudaoTranslator } from './youdao';

export type TranslatorId = 'baidu' | 'youdao';

function selectTranslator(id: TranslatorId) {
  if (id === 'baidu') {
    return BaiduTranslator;
  } else {
    return YoudaoTranslator;
  }
}

export async function createTranslator(id: TranslatorId, glossary: Glossary) {
  return new TranslatorAdapter(await selectTranslator(id).create(), glossary);
}

export function getTranslatorLabel(id: TranslatorId) {
  return selectTranslator(id).label;
}
