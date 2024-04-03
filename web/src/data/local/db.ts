import { LocalVolumeChapter, LocalVolumeMetadata } from '@/model/LocalVolume';
import { DBSchema, lazyOpenDb } from '@/util/db';

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

export const db = lazyOpenDb<VolumesDBSchema>('volumes', 1, {
  upgrade(db, oldVersion, _newVersion, _transaction, _event) {
    if (oldVersion <= 0) {
      db.createObjectStore('metadata', { keyPath: 'id' });
      db.createObjectStore('file', { keyPath: 'id' });
      const store = db.createObjectStore('chapter', { keyPath: 'id' });
      store.createIndex('byVolumeId', 'volumeId');
    }
  },
});
