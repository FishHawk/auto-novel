import { Glossary, TranslatorAdapter } from './adapter';
import { BaiduTranslator } from './baidu';
import { YoudaoTranslator } from './youdao';
import { OpenAiTranslator } from './openai';

export type TranslatorId = 'baidu' | 'youdao' | 'gpt';

export interface TranslatorConfig {
  glossary?: Glossary;
  accessToken?: string;
}

export async function createTranslator(
  id: TranslatorId,
  config: TranslatorConfig
) {
  if (id === 'baidu') {
    return new TranslatorAdapter(
      await BaiduTranslator.create(),
      config.glossary || {}
    );
  } else if (id === 'youdao') {
    return new TranslatorAdapter(
      await YoudaoTranslator.create(),
      config.glossary || {}
    );
  } else {
    if (!config.accessToken) {
      throw new Error('Gpt翻译器需要Token');
    }
    return new TranslatorAdapter(
      await OpenAiTranslator.create(config.accessToken),
      {}
    );
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
