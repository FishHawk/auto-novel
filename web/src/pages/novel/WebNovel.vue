<script lang="ts" setup>
import { WebNovelRepository } from '@/data/api';
import { Result, runCatching } from '@/util/result';
import { useIsWideScreen } from '@/pages/util';

import { ReadableTocItem, WebNovelVM } from './components/common';

const props = defineProps<{ providerId: string; novelId: string }>();

const isWideScreen = useIsWideScreen(850);
const vars = useThemeVars();
const router = useRouter();

const novelResult = ref<Result<WebNovelVM>>();

watch(
  props,
  async ({ providerId, novelId }) => {
    novelResult.value = undefined;
    const result = await runCatching(
      WebNovelRepository.getNovel(providerId, novelId).then((novel) => {
        const novelToc = novel.toc as ReadableTocItem[];
        let order = 0;
        let index = 0;
        for (const it of novelToc) {
          it.index = index;
          it.order = it.chapterId ? order : undefined;
          if (it.chapterId) order += 1;
          index += 1;
        }

        novel.toc = [];
        return <WebNovelVM>{
          ...novel,
          toc: novelToc,
          lastReadChapter: novel.lastReadChapterId
            ? novelToc.find((it) => it.chapterId === novel.lastReadChapterId)
            : undefined,
        };
      })
    );

    if (props.providerId !== providerId || props.novelId !== novelId) return;

    if (!result.ok) {
      const message = result.error.message;
      if (message.includes('小说ID不合适，应当使用：')) {
        const targetNovelPath = message.split('小说ID不合适，应当使用：')[1];
        router.push({ path: `/novel${targetNovelPath}` });
        return;
      }
    }

    novelResult.value = result;
    if (result.ok) {
      document.title = result.value.titleJp;
    }
  },
  { immediate: true }
);

const mixColor = () => {
  const color = vars.value.primaryColor;
  const r = parseInt(color.substring(1, 3), 16);
  const g = parseInt(color.substring(3, 5), 16);
  const b = parseInt(color.substring(5, 7), 16);

  const p = 0.5;
  const mr = (r * p + 255 * (1 - p)).toFixed(0);
  const mg = (g * p).toFixed(0);
  const mb = (b * p).toFixed(0);
  return `rgb(${mr}, ${mg}, ${mb})`;
};
const visitedColor = mixColor();
</script>

<template>
  <div class="layout-content">
    <c-result :result="novelResult" v-slot="{ value: novel }">
      <web-novel-wide
        v-if="isWideScreen"
        :provider-id="providerId"
        :novel-id="novelId"
        :novel="novel"
      />
      <web-novel-narrow
        v-else
        :provider-id="providerId"
        :novel-id="novelId"
        :novel="novel"
      />
    </c-result>
  </div>
</template>

<style>
.toc:visited {
  color: v-bind('visitedColor');
}
</style>
