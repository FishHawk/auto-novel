import { GenericNovelId } from '@/model/Common';
import { WebNovelRepository, WenkuNovelRepository } from '@/data/api';
import { LocalVolumeService } from '@/data/local';
import { Glossary } from '@/model/Glossary';

const updateGlossary = (gnid: GenericNovelId, glossary: Glossary) => {
  if (gnid.type === 'web') {
    return WebNovelRepository.updateGlossary(
      gnid.providerId,
      gnid.novelId,
      glossary
    );
  } else if (gnid.type === 'wenku') {
    return WenkuNovelRepository.updateGlossary(gnid.novelId, glossary);
  } else {
    return LocalVolumeService.updateGlossary(gnid.volumeId, glossary);
  }
};

const exportGlossaryToText = (glossary: Glossary) => {
  return JSON.stringify(glossary, null, 2);
};

const parseGlossaryFromText = (text: string): Glossary | undefined => {
  try {
    const obj = JSON.parse(text);
    if (typeof obj !== 'object') return;
    const inputGlossary: { [key: string]: string } = {};
    for (const jp in obj) {
      const zh = obj[jp];
      if (typeof zh !== 'string') return;
      inputGlossary[jp] = zh;
    }
    return inputGlossary;
  } catch {
    return;
  }
};

const countKatakana = (content: string) => {
  const regexp = /[\u30A0-\u30FF]{2,}/g;
  const matches = content.matchAll(regexp);
  const katakanaCounter = new Map<string, number>();
  for (const match of matches) {
    const w = match[0];
    katakanaCounter.set(w, (katakanaCounter.get(w) || 0) + 1);
  }
  const sortedKatakanaCounter = new Map(
    [...katakanaCounter].sort(([_w1, c1], [_w2, c2]) => c2 - c1)
  );
  return sortedKatakanaCounter;
};

export const GlossaryService = {
  updateGlossary,
  exportGlossaryToText,
  parseGlossaryFromText,
  countKatakana,
};
