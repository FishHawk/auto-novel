import { WebNovelRepository, WenkuNovelRepository } from '@/data/api';
import { LocalVolumeRepository } from '@/data/local';
import { GenericNovelId } from '@/model/Common';
import { Glossary } from '@/model/Glossary';

export const updateGlossary = (gnid: GenericNovelId, glossary: Glossary) => {
  if (gnid.type === 'web') {
    return WebNovelRepository.updateGlossary(
      gnid.providerId,
      gnid.novelId,
      glossary
    );
  } else if (gnid.type === 'wenku') {
    return WenkuNovelRepository.updateGlossary(gnid.novelId, glossary);
  } else {
    return LocalVolumeRepository.updateGlossary(gnid.volumeId, glossary);
  }
};
