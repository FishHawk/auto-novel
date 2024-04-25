import { Locator } from '@/data';
import { WebNovelDto } from '@/model/WebNovel';
import { Result, runCatching } from '@/util/result';

const repo = Locator.webNovelRepository;

type WebNovelStore = {
  novelResult: Result<WebNovelDto> | undefined;
};

export const useWebNovelStore = (providerId: string, novelId: string) => {
  return defineStore(`WebNovel/${providerId}/${novelId}`, {
    state: () =>
      <WebNovelStore>{
        novelResult: undefined,
      },
    actions: {
      async loadNovel(force = false) {
        if (!force && this.novelResult?.ok) {
          return this.novelResult;
        }

        this.novelResult = undefined;
        const result = await runCatching(repo.getNovel(providerId, novelId));
        this.novelResult = result;

        return this.novelResult;
      },

      async updateNovel(json: Parameters<typeof repo.updateNovel>[2]) {
        await repo.updateNovel(providerId, novelId, json);
        this.loadNovel(true);
      },
    },
  })();
};
