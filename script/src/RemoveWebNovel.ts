import { es, mongoDb } from './config.js';

const ARTICLE = 'article';
const COMMENT = 'comment-alt';
const OPERATION_HISTORY = 'operation-history';
const SAKURA_WEB_INCORRECT_CASE = 'sakura-incorrect-case';
const USER = 'user';

const WEB_NOVEL = 'metadata';
const WEB_FAVORITE = 'web-favorite';
const WEB_READ_HISTORY = 'web-read-history';

const WENKU_NOVEL = 'wenku-metadata';
const WENKU_FAVORITE = 'wenku-favorite';

// will deprecate
const WEB_CHAPTER = 'episode';
const TOC_MERGE_HISTORY = 'toc-merge-history';

export const removeWebNovel = async (providerId: string, novelId: string) => {
  try {
    await es.delete({
      id: `${providerId}.${novelId}`,
      index: 'web.2024-06-10',
    });
  } catch {}

  const novel = await mongoDb.collection(WEB_NOVEL).findOne({
    providerId,
    bookId: novelId,
  });
  if (novel !== null) {
    await mongoDb.collection(WEB_FAVORITE).deleteMany({
      novelId: novel._id,
    });
    await mongoDb.collection(WEB_READ_HISTORY).deleteMany({
      novelId: novel._id,
    });
    await mongoDb.collection(WEB_NOVEL).deleteOne({
      providerId,
      bookId: novelId,
    });
  }

  await mongoDb.collection(WEB_CHAPTER).deleteMany({
    providerId,
    bookId: novelId,
  });
  await mongoDb
    .collection(COMMENT)
    .deleteMany({ site: `web-${providerId}-${novelId}` });
};
