import { MD5 } from 'crypto-es/lib/md5';
import { openDB } from 'idb';

import { SegmentCache } from './translator';
import { Glossary } from './type';

function openTestDB() {
  return openDB('test', 3, {
    upgrade(db, _oldVersion, _newVersion, _transaction, _event) {
      try {
        db.createObjectStore('gpt-seg-cache', { keyPath: 'hash' });
      } catch (e) {}
      try {
        db.createObjectStore('sakura-seg-cache', { keyPath: 'hash' });
      } catch (e) {}
    },
  });
}

export async function createSegIndexedDbCache(
  storeName: 'gpt-seg-cache' | 'sakura-seg-cache'
) {
  const db = await openTestDB();
  return <SegmentCache>{
    cacheKey: (
      _segIndex: number,
      seg: string[],
      glossary?: Glossary | undefined
    ): string => MD5(JSON.stringify({ seg, glossary })).toString(),

    get: (cacheKey: string): Promise<string[] | null> =>
      db.get(storeName, cacheKey).then((it) => it?.text),

    save: (cacheKey: string, output: string[]): Promise<void> =>
      db.put(storeName, { hash: cacheKey, text: output }).then(),
  };
}

export async function clearSegIndexedDbCache(
  storeName: 'gpt-seg-cache' | 'sakura-seg-cache'
) {
  const db = await openTestDB();
  db.clear(storeName);
}
