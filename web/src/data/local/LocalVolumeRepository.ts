import { v4 as uuidv4 } from 'uuid';

import { Glossary } from '@/model/Glossary';
import {
  ChapterTranslation,
  LocalVolumeChapter,
  LocalVolumeMetadata,
} from '@/model/LocalVolume';
import { TranslatorId } from '@/model/Translator';

import { createVolume } from './CreateVolume';
import { getTranslationFile } from './GetTranslationFile';
import { LocalVolumeDao } from './LocalVolumeDao';

const deleteVolume = (id: string) =>
  Promise.all([
    LocalVolumeDao.deleteChapterByVolumeId(id),
    LocalVolumeDao.deleteMetadata(id),
    LocalVolumeDao.deleteFile(id),
  ]);

const updateGlossary = (id: string, glossary: Glossary) =>
  LocalVolumeDao.updateMetadata(id, (value) => {
    value.glossary = glossary;
    value.glossaryId = uuidv4();
    return value;
  });

const updateTranslation = async (
  id: string,
  chapterId: string,
  translatorId: TranslatorId,
  translation: ChapterTranslation
) => {
  const chapter = await LocalVolumeDao.updateChapter(
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
  const metadata = await LocalVolumeDao.updateMetadata(
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
};

export const LocalVolumeRepository = {
  getFile: LocalVolumeDao.getFile,
  //
  listVolume: LocalVolumeDao.listMetadata,
  getVolume: LocalVolumeDao.getMetadata,
  createVolume,
  deleteVolume,
  updateGlossary,
  //
  getChapter: LocalVolumeDao.getChapter,
  updateTranslation,
  //
  deleteVolumesDb: LocalVolumeDao.clear,
  //
  getTranslationFile,
};
