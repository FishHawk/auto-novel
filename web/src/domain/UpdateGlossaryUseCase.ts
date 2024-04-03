import { WebNovelRepository, WenkuNovelRepository } from '@/data/api';
import { Locator } from '@/data';
import { GenericNovelId } from '@/model/Common';
import { Glossary } from '@/model/Glossary';

export const updateGlossary = async (
  gnid: GenericNovelId,
  glossary: Glossary
) => {
  if (gnid.type === 'web') {
    await WebNovelRepository.updateGlossary(
      gnid.providerId,
      gnid.novelId,
      glossary
    );
  } else if (gnid.type === 'wenku') {
    await WenkuNovelRepository.updateGlossary(gnid.novelId, glossary);
  } else {
    const repo = await Locator.localVolumeRepository();
    await repo.updateGlossary(gnid.volumeId, glossary);
  }
};
