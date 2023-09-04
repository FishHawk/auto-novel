import { useWindowSize } from '@vueuse/core';
import { computed } from 'vue';

export function useIsDesktop(limit: number) {
  const { width } = useWindowSize();
  const isDesktop = computed(() => width.value > limit);
  return isDesktop;
}
