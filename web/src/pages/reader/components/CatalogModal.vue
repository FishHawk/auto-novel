<script lang="ts" setup>
import { computed, shallowRef, watch } from 'vue';
import { useRoute } from 'vue-router';

import { WebNovelTocItemDto } from '@/data/api/api_web_novel';
import { Result } from '@/data/result';

import { NovelInfo, getNovelToc } from './util';

const props = defineProps<{
  show: boolean;
  novelInfo: NovelInfo;
  chapterId: string;
}>();

const emit = defineEmits<{
  (e: 'update:show', show: boolean): void;
  (e: 'nav', chapterId: string): void;
}>();

const route = useRoute();

const providerId = route.params.providerId as string;
const novelId = route.params.novelId as string;

type TocItem = WebNovelTocItemDto & { key: number };
const tocResult = shallowRef<Result<TocItem[]>>();

const tocNumber = computed(() => {
  if (tocResult.value?.ok !== true) {
    return undefined;
  } else {
    return tocResult.value.value.filter((it) => it.chapterId !== undefined)
      .length;
  }
});

watch(
  () => props.show,
  async (show) => {
    if (show && tocResult.value?.ok !== true) {
      tocResult.value = await getNovelToc(props.novelInfo);
    }
  }
);

const currentKey = computed(() => {
  if (tocResult.value?.ok !== true) {
    return undefined;
  } else {
    return tocResult.value.value.find((it) => it.chapterId === props.chapterId)
      ?.key;
  }
});

const onTocItemClick = (chapterId: string | undefined) => {
  if (chapterId !== undefined) {
    emit('nav', chapterId);
    emit('update:show', false);
  }
};
</script>

<template>
  <c-modal
    :show="show"
    @update:show="$emit('update:show', $event)"
    style="min-height: 30vh"
  >
    <template #header>
      目录
      <n-text
        v-if="tocNumber !== undefined"
        depth="3"
        style="font-size: 12px; margin-left: 12px"
      >
        共{{ tocNumber }}章
      </n-text>
    </template>

    <ResultView
      :result="tocResult"
      :showEmpty="() => false"
      v-slot="{ value: toc }"
    >
      <n-virtual-list
        :item-size="20"
        item-resizable
        :items="toc"
        :default-scroll-key="currentKey"
        :scrollbar-props="{ trigger: 'none' }"
        style="max-height: 60vh"
      >
        <template #default="{ item }">
          <div
            :key="item.index"
            style="width: 100%"
            @click="() => onTocItemClick(item.chapterId)"
          >
            <div style="padding-top: 12px">
              <n-text
                :type="
                  item.key === currentKey
                    ? 'warning'
                    : item.chapterId
                    ? 'success'
                    : 'default'
                "
              >
                {{ item.titleJp }}
              </n-text>
              <br />
              <n-text depth="3">
                {{ item.titleZh }}
              </n-text>
            </div>
          </div>
        </template>
      </n-virtual-list>
    </ResultView>
  </c-modal>
</template>
