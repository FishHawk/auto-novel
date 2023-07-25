import { Glossary, Translator } from './base';
import { BaiduTranslator } from './baidu';
import { YoudaoTranslator } from './youdao';
import { OpenAiTranslator } from './openai';

export type TranslatorId = 'baidu' | 'youdao' | 'gpt';

export interface TranslatorConfig {
  glossary?: Glossary;
  accessToken?: string;
  log?: (message: string) => void;
}

export async function createTranslator(
  id: TranslatorId,
  config: TranslatorConfig
): Promise<Translator> {
  if (id === 'baidu') {
    return await new BaiduTranslator(config.glossary ?? {}, config.log).init();
  } else if (id === 'youdao') {
    return await new YoudaoTranslator(config.glossary ?? {}, config.log).init();
  } else {
    if (!config.accessToken) throw new Error('Gpt翻译器需要Token');
    return new OpenAiTranslator(config.accessToken, config.log);
  }
}

export function getTranslatorLabel(id: TranslatorId) {
  const idToLaber = {
    baidu: '百度',
    youdao: '有道',
    gpt: 'GPT3',
  };
  return idToLaber[id];
}
