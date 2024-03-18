import { v4 as uuidv4 } from 'uuid';

import { Glossary } from '@/model/Glossary';
import {
  ChapterTranslation,
  LocalVolumeChapter,
  LocalVolumeMetadata,
} from '@/model/LocalVolume';
import { TranslatorId } from '@/model/Translator';

import { createVolume, deleteVolume } from './CreateAndDeleteVolumeUseCase';
import { getTranslationFile } from './GetTranslationFileUseCase';
import { LocalVolumeRepository } from './LocalVolumeRepository';

export const LocalVolumeService = {
  getFile: LocalVolumeRepository.getFile,
  //
  listVolume: LocalVolumeRepository.listMetadata,
  getVolume: LocalVolumeRepository.getMetadata,
  createVolume,
  deleteVolume,
  updateGlossary: (id: string, glossary: Glossary) =>
    LocalVolumeRepository.updateMetadata(id, (value) => {
      value.glossary = glossary;
      value.glossaryId = uuidv4();
      return value;
    }),
  //
  getChapter: LocalVolumeRepository.getChapter,
  updateTranslation: async (
    id: string,
    chapterId: string,
    translatorId: TranslatorId,
    translation: ChapterTranslation
  ) => {
    const chapter = await LocalVolumeRepository.updateChapter(
      id,
      chapterId,
      (value: LocalVolumeChapter) => {
        value[translatorId] = translation;
        return value;
      }
    );
    if (chapter === undefined) {
      throw '章节不存在';
    }
    const metadata = await LocalVolumeRepository.updateMetadata(
      id,
      (value: LocalVolumeMetadata) => {
        value.toc
          .filter((it) => it.chapterId === chapterId)
          .forEach((it) => (it[translatorId] = translation.glossaryId));
        return value;
      }
    );
    if (metadata === undefined) {
      throw '小说不存在';
    }
    return metadata.toc.filter((it) => it[translatorId] !== undefined).length;
  },
  //
  deleteVolumesDb: LocalVolumeRepository.clear,
  //
  getTranslationFile,
};
