import { DBSchema, lazyOpenDb } from '@/util/db';

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

const db = lazyOpenDb<SegCacheDBSchema>('test', 3, {
  upgrade(db, _oldVersion, _newVersion, _transaction, _event) {
    try {
      db.createObjectStore('gpt-seg-cache', { keyPath: 'hash' });
    } catch (e) {}
    try {
      db.createObjectStore('sakura-seg-cache', { keyPath: 'hash' });
    } catch (e) {}
  },
});

type CachedSegType = 'gpt-seg-cache' | 'sakura-seg-cache';

export const CachedSegRepository = {
  clear: db.with((db, type: CachedSegType) => db.clear(type)),
  get: db.with((db, type: CachedSegType, hash: string) =>
    db.get(type, hash).then((it) => it?.text)
  ),
  create: db.with((db, type: CachedSegType, hash: string, text: string[]) =>
    db.put(type, { hash, text })
  ),
};
