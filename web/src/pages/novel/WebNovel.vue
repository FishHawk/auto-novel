<script lang="ts" setup>
import { useIsWideScreen } from '@/pages/util';
import { useWebNovelStore } from './WebNovelStore';

const { providerId, novelId } = defineProps<{
  providerId: string;
  novelId: string;
}>();

const isWideScreen = useIsWideScreen(850);
const router = useRouter();

const store = useWebNovelStore(providerId, novelId);
const { novelResult } = storeToRefs(store);

store.loadNovel().then((result) => {
  if (result && !result.ok) {
    const message = result.error.message;
    if (message.includes('小说ID不合适，应当使用：')) {
      const targetNovelPath = message.split('小说ID不合适，应当使用：')[1];
      router.push({ path: `/novel${targetNovelPath}` });
      return;
    }
  }

  if (result?.ok) {
    document.title = result.value.titleJp;
  }
});

const vars = useThemeVars();
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
