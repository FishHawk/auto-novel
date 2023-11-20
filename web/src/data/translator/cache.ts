import { MD5 } from 'crypto-es/lib/md5';
import { openDB } from 'idb';

import { SegmentCache } from './translator';
import { Glossary } from './type';

export async function createSegIndexedDbCache(storeName: string) {
  const db = await openDB('test', 1, {
    upgrade(db, _oldVersion, _newVersion, _transaction, _event) {
      db.createObjectStore(storeName, { keyPath: 'hash' });
    },
  });

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
