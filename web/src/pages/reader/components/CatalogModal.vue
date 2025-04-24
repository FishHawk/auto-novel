<script lang="ts" setup>
import {
  SortOutlined,
  KeyboardArrowUpRound,
  KeyboardArrowDownRound,
} from '@vicons/material';
import { Locator } from '@/data';
import { GenericNovelId } from '@/model/Common';
import { useWebNovelStore } from '@/pages/novel/WebNovelStore';
import { Ok, Result, runCatching } from '@/util/result';
import { ReadableTocItem } from '@/pages/novel/components/common';
import { useTocExpansion } from '@/pages/novel/components/UseTocExpansion';
import ChapterTocList from '@/components/ChapterTocList.vue';
import { useIsWideScreen } from '@/pages/util';

const props = defineProps<{
  show: boolean;
  gnid: GenericNovelId;
  chapterId: string;
}>();

const emit = defineEmits<{
  'update:show': [boolean];
}>();

type TocItem = ReadableTocItem & {
  key: number;
};

const tocResult = shallowRef<Result<TocItem[]>>();

const tocData = computed(() =>
  tocResult.value?.ok ? tocResult.value.value : undefined,
);

const tocNumber = computed(() => {
  return tocData.value?.filter((it) => it.chapterId !== undefined).length;
});

const { setting } = Locator.settingRepository();
const sortReverse = computed(() => setting.value.tocSortReverse);

const isWideScreen = useIsWideScreen();

// const defaultTocExpanded = ref(true);
const defaultTocExpanded = computed(() => {
  return isWideScreen.value || setting.value.tocExpandAllInNarrowScreen;
});

const { expandedNames, hasSeparators, isAnyExpanded, toggleAll, tocSections } =
  useTocExpansion(
    tocData,
    defaultTocExpanded,
    computed(() => props.chapterId),
  );

watch(
  () => props.show,
  async (show) => {
    if (show) {
      if (tocResult.value?.ok !== true) {
        const getWebToc = async (providerId: string, novelId: string) => {
          const store = useWebNovelStore(providerId, novelId);
          const result = await store.loadNovel();
          if (result.ok) {
            let order = 0;
            const tocItems = result.value.toc.map((it, index) => {
              const tocItem = <TocItem>{
                ...it,
                key: index,
                order: it.chapterId ? order : undefined,
              };
              if (it.chapterId) order += 1;
              return tocItem;
            });
            return Ok(tocItems);
          } else {
            return result;
          }
        };

        const getLocalToc = async (volumeId: string) => {
          const repo = await Locator.localVolumeRepository();
          const volume = await repo.getVolume(volumeId);
          if (volume === undefined) throw Error('小说不存在');
          return volume.toc.map(
            (it, index) =>
              <TocItem>{
                titleJp: it.chapterId,
                chapterId: it.chapterId,
                key: index,
              },
          );
        };

        const gnid = props.gnid;
        if (gnid.type === 'web') {
          tocResult.value = await getWebToc(gnid.providerId, gnid.novelId);
        } else if (gnid.type === 'wenku') {
          throw '不支持文库';
        } else {
          tocResult.value = await runCatching(getLocalToc(gnid.volumeId));
        }
      }
    }
  },
);

const currentKey = computed(() => {
  return tocData.value?.find((it) => it.chapterId === props.chapterId)?.key;
});

const onTocItemClick = (item: ReadableTocItem) => {
  if (item.chapterId !== undefined) {
    emit('update:show', false);
  }
};
</script>

<template>
  <c-modal
    :show="show"
    @update:show="$emit('update:show', $event)"
    style="min-height: 30vh; max-height: 80vh"
    content-style="overflow: auto;"
  >
    <template #header>
      <div style="display: flex; align-items: baseline">
        <span>目录</span>
        <n-text
          v-if="tocNumber !== undefined"
          depth="3"
          style="font-size: 12px; margin-left: 12px"
        >
          共{{ tocNumber }}章
        </n-text>
        <div style="flex: 1" />
        <c-button
          v-if="hasSeparators"
          :label="isAnyExpanded ? '折叠' : '展开'"
          :icon="isAnyExpanded ? KeyboardArrowUpRound : KeyboardArrowDownRound"
          quaternary
          size="small"
          :round="false"
          @action="toggleAll"
          style="margin-right: 8px"
        />
        <c-button
          :label="setting.tocSortReverse ? '倒序' : '正序'"
          :icon="SortOutlined"
          quaternary
          size="small"
          :round="false"
          @action="setting.tocSortReverse = !setting.tocSortReverse"
        />
      </div>
    </template>

    <c-result :result="tocResult" v-slot="{ value: _ }">
      <chapter-toc-list
        v-if="gnid.type === 'web' && tocSections"
        :toc-sections="tocSections"
        v-model:expanded-names="expandedNames"
        :last-read-chapter-id="chapterId"
        :provider-id="gnid.providerId"
        :novel-id="gnid.novelId"
        :sort-reverse="sortReverse"
        :mode="{
          narrow: !isWideScreen,
          catalog: true,
          collapse: false,
        }"
        @item-click="onTocItemClick"
        style="height: 100%"
      />
      <chapter-toc-list
        v-else-if="gnid.type === 'local' && tocSections"
        :toc-sections="tocSections"
        v-model:expanded-names="expandedNames"
        :last-read-chapter-id="chapterId"
        :provider-id="gnid.volumeId"
        :novel-id="gnid.volumeId"
        :sort-reverse="sortReverse"
        :mode="{
          narrow: !isWideScreen,
          catalog: true,
          collapse: false,
        }"
        @item-click="onTocItemClick"
        style="height: 100%"
      />
    </c-result>
  </c-modal>
</template>
