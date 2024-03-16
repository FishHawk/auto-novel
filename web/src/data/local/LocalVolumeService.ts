import { v4 as uuidv4 } from 'uuid';

import { TranslatorId } from '@/data/translator/translator';
import {
  ChapterTranslation,
  LocalVolumeChapter,
  LocalVolumeMetadata,
} from '@/model/LocalVolume';
import { Glossary } from '@/model/Glossary';

import { createVolume, deleteVolume } from './CreateAndDeleteVolumeUseCase';
import { getReadableChapter } from './GetReaderableChapterUseCase';
import { LocalVolumeRepository } from './LocalVolumeRepository';
import { getTranslationFile } from './GetTranslationFileUseCase';

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
  getReadableChapter,
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
