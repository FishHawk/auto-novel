import { GenericNovelId } from '@/model/Common';
import { syncUseLocalStorage } from '@/util/storage';

interface ReadPosition {
  chapterId: string;
  scrollY: number;
}

type ReaderPositions = Record<string, ReadPosition | undefined>;

const syncStorage = syncUseLocalStorage<ReaderPositions>('readPosition', {});

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

export const ReadPositionRepository = {
  addPosition,
  getPosition,
};
