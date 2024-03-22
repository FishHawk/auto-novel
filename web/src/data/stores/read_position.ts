import { GenericNovelId, gnidToString } from '@/model/Common';

export interface ReadPosition {
  chapterId: string;
  scrollY: number;
}

const safeJson = <T extends object>(text: string) => {
  try {
    return JSON.parse(text) as T;
  } catch (err) {
    return undefined;
  }
};

const getItemLocalStorage = <T extends object>(
  key: string,
  defaultValue: T
) => {
  const text = window.localStorage.getItem(key) ?? '';
  const value = safeJson<T>(text) ?? defaultValue;
  return value;
};

const setItemLocalStorage = <T extends object>(key: string, value: T) => {
  const text = JSON.stringify(value);
  window.localStorage.setItem(key, text);
};

const key = 'readPosition';
type Store = Record<string, ReadPosition | undefined>;

const addPosition = (gnid: GenericNovelId, position: ReadPosition) => {
  const store = getItemLocalStorage<Store>(key, {});
  store[gnidToString(gnid)] = position;
  setItemLocalStorage(key, store);
};

const getPosition = (gnid: GenericNovelId) => {
  const store = getItemLocalStorage<Store>(key, {});
  return store[gnidToString(gnid)];
};

export const ReadPositionStore = {
  addPosition,
  getPosition,
};
