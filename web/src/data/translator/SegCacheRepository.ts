import { DBSchema, openDB } from 'idb';

interface SegCacheDBSchema extends DBSchema {
  'gpt-seg-cache': {
    key: string;
    value: { hash: string; text: string[] };
  };
  'sakura-seg-cache': {
    key: string;
    value: { hash: string; text: string[] };
  };
}

type CachedSegType = 'gpt-seg-cache' | 'sakura-seg-cache';

export const createCachedSegRepository = async () => {
  const db = await openDB<SegCacheDBSchema>('volumes', 1, {
    upgrade(db, _oldVersion, _newVersion, _transaction, _event) {
      try {
        db.createObjectStore('gpt-seg-cache', { keyPath: 'hash' });
      } catch (e) {}
      try {
        db.createObjectStore('sakura-seg-cache', { keyPath: 'hash' });
      } catch (e) {}
    },
  });

  return {
    clear: (type: CachedSegType) => db.clear(type),
    get: (type: CachedSegType, hash: string) =>
      db.get(type, hash).then((it) => it?.text),
    create: (type: CachedSegType, hash: string, text: string[]) =>
      db.put(type, { hash, text }),
  };
};
