<script lang="ts" setup>
import { useIsWideScreen } from '@/pages/util';
import { useWebNovelStore } from './WebNovelStore';

const { providerId, novelId } = defineProps<{
  providerId: string;
  novelId: string;
}>();

const isWideScreen = useIsWideScreen();
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
