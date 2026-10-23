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
