import { LocalVolumeChapter, LocalVolumeMetadata } from '@/model/LocalVolume';
import { Mutator } from '@/util/db';

import { db } from './db';

const clear = db.deleteDb;

//Metadata
const listMetadata = db.with((db) => db.getAll('metadata'));
const getMetadata = db.with((db, id: string) => db.get('metadata', id));
const deleteMetadata = db.with((db, id: string) => db.delete('metadata', id));
const createMetadata = db.with((db, value: LocalVolumeMetadata) =>
  db.put('metadata', value)
);
const updateMetadata = db.with(
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
);

// File
const getFile = db.with((db, id: string) => db.get('file', id));
const deleteFile = db.with((db, id: string) => db.delete('file', id));
const createFile = db.with((db, id: string, file: File) =>
  db.put('file', { id, file })
);

// Chapter
const getChapter = db.with((db, id: string, chapterId: string) =>
  db.get('chapter', `${id}/${chapterId}`)
);
const createChapter = db.with((db, chapter: LocalVolumeChapter) =>
  db.put('chapter', chapter)
);
const updateChapter = db.with(
  async (
    db,
    id: string,
    chapterId: string,
    mutator: Mutator<LocalVolumeChapter>
  ) => {
    const tx = db.transaction('chapter', 'readwrite');
    let value = await tx.store.get(`${id}/${chapterId}`);
    if (value !== undefined) {
      value = mutator(value);
      await tx.store.put(value);
    }
    await tx.done;
    return value;
  }
);
const deleteChapterByVolumeId = db.with(async (db, id: string) => {
  const tx = db.transaction('chapter', 'readwrite');
  for await (const cursor of tx.store.index('byVolumeId').iterate(id)) {
    tx.store.delete(cursor.primaryKey);
  }
  await tx.done;
});

export const LocalVolumeDao = {
  clear,
  //
  listMetadata,
  getMetadata,
  deleteMetadata,
  createMetadata,
  updateMetadata,
  //
  getFile,
  deleteFile,
  createFile,
  //
  getChapter,
  createChapter,
  updateChapter,
  deleteChapterByVolumeId,
};
