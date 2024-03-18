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

export const GlossaryService = {
  updateGlossary,
  exportGlossaryToText,
  parseGlossaryFromText,
};
