import { createGlobalState } from '@vueuse/core';

import { Locator } from '@/data';
import { WenkuNovelDto } from '@/model/WenkuNovel';
import { Result, runCatching } from '@/util/result';

export const useWenkuNovelStore = createGlobalState(() => {
  // state
  const novelResult = ref<Result<WenkuNovelDto>>();
  const id = ref<string>();

  // actions
  const load = async (novelId: string, force = false) => {
    if (!force && id.value === novelId && novelResult.value?.ok) {
      return novelResult.value;
    }

    novelResult.value = undefined;
    id.value = novelId;
    const result = await runCatching(
      Locator.wenkuNovelRepository.getNovel(novelId)
    );
    if (novelId !== id.value) return;

    if (result.ok) {
      result.value.volumeZh = result.value.volumeZh.sort((a, b) =>
        a.localeCompare(b)
      );
      result.value.volumeJp = result.value.volumeJp.sort((a, b) =>
        a.volumeId.localeCompare(b.volumeId)
      );
    }
    novelResult.value = result;
    return result;
  };

  return { novelResult, load };
});
