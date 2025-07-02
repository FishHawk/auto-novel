import { useEventListener } from '@vueuse/core';

export function useLocalStorage<T extends object>(key: string, defaults: T) {
  return useStorage<T>(key, defaults, window.localStorage);
}

export function useSessionStorage<T extends object>(key: string, defaults: T) {
  return useStorage<T>(key, defaults, window.sessionStorage);
}

function useStorage<T extends object>(
  key: string,
  defaults: T,
  storage: Storage,
): Ref<T> {
  const onError = (e: unknown) => {
    console.error(e);
  };

  const serializer = {
    read: (v: string) => JSON.parse(v),
    write: (v: T) => JSON.stringify(v),
  };

  const data: Ref<T> = ref<T>(defaults) as Ref<T>;
  try {
    data.value = read();
  } catch (e) {
    onError(e);
  }

  const { pause: pauseWatch, resume: resumeWatch } = watch(
    data,
    () => write(data.value),
    { flush: 'pre', deep: true },
  );

  useEventListener(
    window,
    'storage',
    (ev: StorageEvent) => {
      if (ev.storageArea !== storage) return;

      if (ev.key !== key) {
        // 当调用clear()时，ev.key为null
        if (ev.key == null) data.value = defaults;
        return;
      }

      pauseWatch();
      try {
        if (ev.newValue !== serializer.write(data.value)) data.value = read(ev);
      } catch (e) {
        onError(e);
      }
      nextTick(resumeWatch);
    },
    { passive: true },
  );

  function write(v: T) {
    try {
      const oldValue = storage.getItem(key);
      const newValue = serializer.write(v);
      if (oldValue !== newValue) {
        storage.setItem(key, newValue);
      }
    } catch (e) {
      onError(e);
    }
  }

  function read(event?: StorageEvent): T {
    const rawValue = event ? event.newValue : storage.getItem(key);

    if (rawValue == null) {
      return defaults;
    } else if (!event) {
      return { ...defaults, ...serializer.read(rawValue) };
    } else {
      return serializer.read(rawValue);
    }
  }

  return data;
}
