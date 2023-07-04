<script lang="ts" setup>
import { SortFilled } from '@vicons/material';
import { computed } from 'vue';
import { createReusableTemplate } from '@vueuse/core';

import { WebNovelTocItemDto } from '@/data/api/api_web_novel';
import { useSettingStore } from '@/data/stores/setting';
import { useIsDesktop } from '@/data/util';

const [DefineTocItem, ReuseTocItem] = createReusableTemplate<{
  item: {
    titleJp: string;
    titleZh?: string;
    chapterId?: string;
    createAt?: string;
  };
}>();

const isDesktop = useIsDesktop(600);

const props = defineProps<{
  providerId: string;
  novelId: string;
  toc: WebNovelTocItemDto[];
}>();

function readableDate(createAt: number) {
  return new Date(createAt * 1000).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

const setting = useSettingStore();
const readableToc = props.toc.map((it) => {
  return {
    titleJp: it.titleJp,
    titleZh: it.titleZh,
    chapterId: it.chapterId,
    createAt: it.createAt ? readableDate(it.createAt) : undefined,
  };
});
const reverseToc = computed(() => readableToc.slice().reverse());
</script>

<template>
  <section>
    <DefineTocItem v-slot="{ item }">
      <div v-if="isDesktop" style="width: 100; display: flex; padding: 6px">
        <span style="flex: 1 1 0">{{ item.titleJp }}</span>
        <span style="color: grey; flex: 1 1 0">{{ item.titleZh }}</span>
        <span style="color: grey; width: 110px">{{ item.createAt }}</span>
      </div>

      <div v-else style="width: 100; padding: 6px">
        {{ item.titleJp }}
        <br />
        <span style="color: grey">{{ item.titleZh }}</span>
        <br />
        <span style="color: grey">{{ item.createAt }}</span>
      </div>
    </DefineTocItem>

    <SectionHeader title="目录">
      <n-button @click="setting.tocSortReverse = !setting.tocSortReverse">
        <template #icon>
          <n-icon :component="SortFilled" />
        </template>
        {{ setting.tocSortReverse ? '倒序' : '正序' }}
      </n-button>
    </SectionHeader>

    <n-list>
      <n-list-item
        v-for="tocItem in setting.tocSortReverse ? reverseToc : readableToc"
        style="padding: 0px"
      >
        <n-a
          v-if="tocItem.chapterId"
          :href="`/novel/${providerId}/${novelId}/${tocItem.chapterId}`"
        >
          <ReuseTocItem :item="tocItem" />
        </n-a>
        <ReuseTocItem v-else :item="tocItem" />
      </n-list-item>
    </n-list>
  </section>
</template>
