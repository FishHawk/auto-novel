import { DBSchema, Mutator, lazyOpenDb } from '@/data/db';
import { LocalVolumeChapter, LocalVolumeMetadata } from '@/model/LocalVolume';

interface VolumesDBSchema extends DBSchema {
  metadata: {
    key: string;
    value: LocalVolumeMetadata;
  };
  file: {
    key: string;
    value: {
      id: string;
      file: File;
    };
  };
  chapter: {
    key: string;
    value: LocalVolumeChapter;
    indexes: { byVolumeId: string };
  };
}

const db = lazyOpenDb<VolumesDBSchema>('volumes', 1, {
  upgrade(db, oldVersion, _newVersion, _transaction, _event) {
    if (oldVersion <= 0) {
      db.createObjectStore('metadata', { keyPath: 'id' });
      db.createObjectStore('file', { keyPath: 'id' });
      const store = db.createObjectStore('chapter', { keyPath: 'id' });
      store.createIndex('byVolumeId', 'volumeId');
    }
  },
});

export const LocalVolumeRepository = {
  clear: db.deleteDb,
  //
  listMetadata: db.with((db) => db.getAll('metadata')),
  getMetadata: db.with((db, id: string) => db.get('metadata', id)),
  deleteMetadata: db.with((db, id: string) => db.delete('metadata', id)),
  createMetadata: db.with((db, value: LocalVolumeMetadata) =>
    db.put('metadata', value)
  ),
  updateMetadata: db.with(
    async (db, id: string, mutator: Mutator<LocalVolumeMetadata>) => {
      const tx = db.transaction('metadata', 'readwrite');
      let value = await tx.store.get(id);
      if (value !== undefined) {
        value = mutator(value);
        await tx.store.put(value);
      }
      await tx.done;
      return value;
    }
  ),
  //
  getFile: db.with((db, id: string) => db.get('file', id)),
  deleteFile: db.with((db, id: string) => db.delete('file', id)),
  createFile: db.with((db, id: string, file: File) =>
    db.put('file', { id, file })
  ),
  //
  getChapter: db.with((db, id: string, chapterId: string) =>
    db.get('chapter', `${id}/${chapterId}`)
  ),
  createChapter: db.with((db, chapter: LocalVolumeChapter) =>
    db.put('chapter', chapter)
  ),
  updateChapter: db.with(
    async (db, id: string, mutator: Mutator<LocalVolumeChapter>) => {
      const tx = db.transaction('chapter', 'readwrite');
      let value = await tx.store.get(id);
      if (value !== undefined) {
        value = mutator(value);
        await tx.store.put(value);
      }
      await tx.done;
      return value;
    }
  ),
  deleteChapterByVolumeId: db.with(async (db, id: string) => {
    const tx = db.transaction('chapter', 'readwrite');
    for await (const cursor of tx.store.index('byVolumeId').iterate(id)) {
      tx.store.delete(cursor.primaryKey);
    }
    await tx.done;
  }),
};
