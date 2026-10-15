import {
  useBreakpoints as useBreakpointsInner,
  useWindowSize,
} from '@vueuse/core';

export const useIsWideScreen = (limit: number = 840) => {
  const { width } = useWindowSize();
  return computed(() => width.value > limit);
};

export const useBreakPoints = () =>
  useBreakpointsInner({
    mobile: 0,
    tablet: 540,
    desktop: 1200,
  });

type KeyPredicate = (event: KeyboardEvent) => boolean;
type KeyFilter = string | string[] | KeyPredicate;

const createKeyPredicate = (keyFilter: KeyFilter): KeyPredicate => {
  if (typeof keyFilter === 'function') {
    return keyFilter;
  } else if (typeof keyFilter === 'string') {
    return (e: KeyboardEvent) => !e.isComposing && e.key === keyFilter;
  } else if (Array.isArray(keyFilter)) {
    return (e: KeyboardEvent) => !e.isComposing && keyFilter.includes(e.key);
  }
  return () => true;
};

export const onKeyDown = (
  key: KeyFilter,
  handler: (event: KeyboardEvent) => void,
) => {
  const listener = (e: KeyboardEvent) => {
    const predicate = createKeyPredicate(key);
    if (predicate(e)) handler(e);
  };
  onActivated(() => document.addEventListener('keydown', listener));
  onDeactivated(() => document.removeEventListener('keydown', listener));
};
