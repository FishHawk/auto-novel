import { MD5 } from 'crypto-es/lib/md5';
import { openDB } from 'idb';

import { Glossary } from '../type';

const openTestDB = () =>
  openDB('test', 3, {
    upgrade(db, _oldVersion, _newVersion, _transaction, _event) {
      try {
        db.createObjectStore('gpt-seg-cache', { keyPath: 'hash' });
      } catch (e) {}
      try {
        db.createObjectStore('sakura-seg-cache', { keyPath: 'hash' });
      } catch (e) {}
    },
  });

export interface SegmentCache {
  cacheKey(segIndex: number, seg: string[], extra?: any): string;
  get(cacheKey: string): Promise<string[] | null>;
  save(cacheKey: string, output: string[]): Promise<void>;
  clear(): Promise<void>;
}

export const createSegIndexedDbCache = async (
  storeName: 'gpt-seg-cache' | 'sakura-seg-cache'
) => {
  const db = await openTestDB();
  return <SegmentCache>{
    cacheKey: (_segIndex: number, seg: string[], extra?: any): string =>
      MD5(JSON.stringify({ seg, extra })).toString(),

    get: (cacheKey: string): Promise<string[] | null> =>
      db.get(storeName, cacheKey).then((it) => it?.text),

    save: (cacheKey: string, output: string[]): Promise<void> =>
      db.put(storeName, { hash: cacheKey, text: output }).then(),
    clear: () => db.clear(storeName),
  };
};
