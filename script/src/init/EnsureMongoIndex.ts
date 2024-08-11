import { IndexDescription } from 'mongodb';

import { MONGO } from './DbMongo.js';

const INDEX_SPECS: { [key: string]: IndexDescription[] } = {
  [MONGO.ARTICLE]: [
    // Why?
    { key: { updateAt: 1 }, partialFilterExpression: { pinned: true } },
    { key: { pinned: 1, updateAt: 1 } },
  ],
  [MONGO.COMMENT]: [
    { key: { site: 1, parent: 1, _id: 1 } }, //
  ],
  [MONGO.GLOSSARY]: [
    { key: { novelType: 1, novelId: 1, createdAt: 1 } }, //
  ],
  [MONGO.OPERATION_HISTORY]: [
    { key: { createAt: 1 } }, //
  ],
  [MONGO.USER]: [
    { key: { email: 1 }, unique: true },
    { key: { username: 1 }, unique: true },
  ],
  //
  [MONGO.WEB_FAVORITE]: [
    { key: { userId: 1, novelId: 1 }, unique: true },
    { key: { userId: 1, createAt: 1 } },
    { key: { userId: 1, updateAt: 1 } },
  ],
  [MONGO.WEB_READ_HISTORY]: [
    { key: { userId: 1, novelId: 1 }, unique: true },
    { key: { userId: 1, createAt: 1 } },
    { key: { createAt: 1 }, expireAfterSeconds: 100 * 24 * 3600 }, // 100天
  ],
  [MONGO.WEB_NOVEL]: [
    { key: { providerId: 1, bookId: 1 }, unique: true }, //
  ],
  [MONGO.WEB_CHAPTER]: [
    { key: { providerId: 1, bookId: 1, episodeId: 1 }, unique: true }, //
  ],
  //
  [MONGO.WENKU_FAVORITE]: [
    { key: { userId: 1, novelId: 1 }, unique: true },
    { key: { userId: 1, createAt: 1 } },
    { key: { userId: 1, updateAt: 1 } },
  ],
};

const printIndex = async (indexes: any[]) => {
  const table: any = {};
  for (const index of indexes) {
    const name = index['name'];
    const key = JSON.stringify(index['key']);

    delete index['v'];
    delete index['name'];
    delete index['key'];

    if (Object.keys(index).length === 0) {
      table[name] = { key };
    } else {
      const options = JSON.stringify(index);
      table[name] = { key, options };
    }
  }
  console.table(table);
};

const ensureIndex = async (name: string) => {
  const ensureCollection = async (name: string) => {
    try {
      return MONGO.col(name);
    } catch {
      await MONGO.db.createCollection(name);
      return MONGO.col(name);
    }
  };

  const indexSpecs = INDEX_SPECS[name];
  const col = await ensureCollection(name);
  if (indexSpecs.length > 0) {
    await col.createIndexes(indexSpecs);
  }
  const indexes = await col.listIndexes().toArray();
  console.log(`MongoIndex: ${col.collectionName}`);
  const matched = indexes.length !== indexSpecs.length + 1;
  await printIndex(indexes);
  return matched;
};

export const ensureMongoIndex = async () => {
  const names: string[] = [
    MONGO.ARTICLE,
    MONGO.COMMENT,
    MONGO.GLOSSARY,
    MONGO.OPERATION_HISTORY,
    MONGO.USER,
    //
    MONGO.WEB_NOVEL,
    MONGO.WEB_CHAPTER,
    MONGO.WEB_FAVORITE,
    MONGO.WEB_READ_HISTORY,
    //
    MONGO.WENKU_FAVORITE,
  ];
  const unmatched: string[] = [];

  for (const name of names) {
    const matched = await ensureIndex(name);
    if (!matched) {
      unmatched.push(name);
    }
  }

  if (unmatched.length > 0) {
    console.log(`警告，索引数量不匹配：${JSON.stringify(unmatched)}`);
  }
};
