import { RemovableRef, useLocalStorage } from '@vueuse/core';
import { safeJson } from '.';

export type StorageMigrationFunction<T> = (value: RemovableRef<T>) => void;

export const lazyUseLocalStorage = <T>(
  key: string,
  initialValue: T,
  migration?: (value: Ref<T>) => void
) => {
  let sRef: Ref<T>;

  const getStorageRef = () => {
    if (sRef === undefined) {
      sRef = useLocalStorage<T>(key, initialValue);
      if (migration) {
        migration(sRef);
      }
    }
    return sRef;
  };

  return {
    ref: getStorageRef,
    with: <Args extends Array<any>, Return>(
      fn: (ref: Ref<T>, ...args: Args) => Return
    ) => {
      return (...args: Args): Return => {
        return fn(getStorageRef(), ...args);
      };
    },
  };
};

interface LocalStorage<T> {
  get(): T;
  set(value: T): void;
}

export const syncUseLocalStorage = <T extends object>(
  key: string,
  initialValue: T
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
