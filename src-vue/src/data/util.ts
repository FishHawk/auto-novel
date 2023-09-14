import { useWindowSize } from '@vueuse/core';
import { computed } from 'vue';

import { TranslatorId } from '@/data/translator/translator';

export function useIsDesktop(limit: number) {
  const { width } = useWindowSize();
  const isDesktop = computed(() => width.value > limit);
  return isDesktop;
}

export function getTranslatorLabel(id: TranslatorId) {
  const idToLaber = {
    baidu: '百度',
    youdao: '有道',
    gpt: 'GPT3',
  };
  return idToLaber[id];
}
