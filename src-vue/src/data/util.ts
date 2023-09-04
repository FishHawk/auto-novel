import { useWindowSize } from '@vueuse/core';
import { computed } from 'vue';

export function formatDate(epochSeconds: number) {
  return new Date(epochSeconds * 1000).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function useIsDesktop(limit: number) {
  const { width } = useWindowSize();
  const isDesktop = computed(() => width.value > limit);
  return isDesktop;
}
