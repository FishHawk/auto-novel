import { createGlobalState } from '@vueuse/core';

import { Locator } from '@/data';
import { WebNovelDto } from '@/model/WebNovel';
import { Result, runCatching } from '@/util/result';

export const useWebNovelStore = createGlobalState(() => {
  // state
  const novelResult = ref<Result<WebNovelDto>>();
  const ids = ref<[string, string]>();

  // actions
  const load = async (providerId: string, novelId: string, force = false) => {
    if (
      !force &&
      ids.value?.[0] === providerId &&
      ids.value?.[1] === novelId &&
      novelResult.value?.ok
    ) {
      return novelResult.value;
    }

    novelResult.value = undefined;
    ids.value = [providerId, novelId];
    const result = await runCatching(
      Locator.webNovelRepository.getNovel(providerId, novelId)
    );
    if (ids.value?.[0] !== providerId || ids.value?.[1] !== novelId) return;
    novelResult.value = result;
    return result;
  };

  return { novelResult, load };
});
