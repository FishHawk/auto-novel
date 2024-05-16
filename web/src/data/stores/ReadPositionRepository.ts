import { GenericNovelId } from '@/model/Common';
import { safeJson } from '@/util';

interface LocalStorage<T> {
  get(): T;
  set(value: T): void;
}

const syncLocalStorage = <T extends object>(
  key: string,
  initialValue: T,
): LocalStorage<T> => {
  return {
    get: () => {
      const text = window.localStorage.getItem(key) ?? '';
      const value = safeJson<T>(text) ?? initialValue;
      return value;
    },
    set: (value: T) => {
      const text = JSON.stringify(value);
      window.localStorage.setItem(key, text);
    },
  };
};

interface ReadPosition {
  chapterId: string;
  scrollY: number;
}

type ReaderPositions = Record<string, ReadPosition | undefined>;

export const createReadPositionRepository = () => {
  const syncStorage = syncLocalStorage<ReaderPositions>('readPosition', {});

  const addPosition = (gnid: GenericNovelId, position: ReadPosition) => {
    const positions = syncStorage.get();
    if (position.scrollY === 0) {
      delete positions[GenericNovelId.toString(gnid)];
    } else {
      positions[GenericNovelId.toString(gnid)] = position;
    }
    syncStorage.set(positions);
  };

  const getPosition = (gnid: GenericNovelId) => {
    const positions = syncStorage.get();
    return positions[GenericNovelId.toString(gnid)];
  };

  return {
    addPosition,
    getPosition,
  };
};
