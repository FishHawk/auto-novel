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
import { LocalVolumeDao, createLocalVolumeDao } from './LocalVolumeDao';

export const createLocalVolumeRepository = async () => {
  const dao = await createLocalVolumeDao();

  const deleteVolume = (id: string) =>
    Promise.all([
      dao.deleteChapterByVolumeId(id),
      dao.deleteMetadata(id),
      dao.deleteFile(id),
    ]);

  const updateGlossary = (id: string, glossary: Glossary) =>
    dao.updateMetadata(id, (value) => {
      value.glossary = glossary;
      value.glossaryId = uuidv4();
      return value;
    });

  const updateReadAt = (id: string) =>
    dao.updateMetadata(id, (value) => {
      value.readAt = Date.now();
      return value;
    });

  const updateFavoriteId = (id: string, favoriteId: string) =>
    dao.updateMetadata(id, (value) => {
      value.favoriteId = favoriteId;
      return value;
    });

  const updateTranslation = async (
    id: string,
    chapterId: string,
    translatorId: TranslatorId,
    translation: ChapterTranslation,
  ) => {
    const chapter = await dao.updateChapter(
      id,
      chapterId,
      (value: LocalVolumeChapter) => {
        value[translatorId] = translation;
        return value;
      },
    );
    if (chapter === undefined) {
      throw '章节不存在';
    }
    const metadata = await dao.updateMetadata(
      id,
      (value: LocalVolumeMetadata) => {
        value.toc
          .filter((it) => it.chapterId === chapterId)
          .forEach((it) => (it[translatorId] = translation.glossaryId));
        return value;
      },
    );
    if (metadata === undefined) {
      throw '小说不存在';
    }
    return metadata.toc.filter((it) => it[translatorId] !== undefined).length;
  };

  const listFavored = () => dao.listFavorite().then((it) => it.reverse());

  const createFavored = (title: string) =>
    dao.createFavorite({
      id: uuidv4(),
      title,
    });

  const updateFavored = (id: string, title: string) =>
    dao.updateFavorite(id, (value) => {
      value.title = title;
      return value;
    });

  const deleteFavored = (id: string) => {
    if (id === 'default') {
      throw new Error('无法删除默认收藏夹');
    }
    return dao.deleteFavorite(id);
  };

  const bind = <Args extends Array<any>, Return>(
    fn: (dao: LocalVolumeDao, ...args: Args) => Promise<Return>,
  ) => {
    return async (...args: Args): Promise<Return> => {
      return fn(dao, ...args);
    };
  };

  return {
    getFile: dao.getFile,
    //
    listVolume: dao.listMetadata,
    getVolume: dao.getMetadata,
    createVolume: bind(createVolume),
    deleteVolume,
    updateGlossary,
    updateReadAt,
    updateFavoriteId,
    //
    getChapter: dao.getChapter,
    updateTranslation,
    //
    getTranslationFile: bind(getTranslationFile),
    //
    listFavored,
    createFavored,
    updateFavored,
    deleteFavored,
  };
};
