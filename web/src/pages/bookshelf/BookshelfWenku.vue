<script lang="ts" setup>
import { ChecklistOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { WenkuNovelOutlineDto } from '@/model/WenkuNovel';
import { runCatching } from '@/util/result';

import { useIsWideScreen } from '@/pages/util';
import NovelListWenku from '../list/components/NovelListWenku.vue';
import { Loader } from '../list/components/NovelPage.vue';

const props = defineProps<{
  page: number;
  selected: number[];
  favoredId: string;
}>();

const isWideScreen = useIsWideScreen();

const options = [
  {
    label: '排序',
    tags: ['更新时间', '收藏时间'],
  },
];

const loader = computed<Loader<WenkuNovelOutlineDto>>(() => {
  const { favoredId } = props;
  return (page, _query, selected) => {
    const optionNth = (n: number): string => options[n].tags[selected[n]];
    const optionSort = () => {
      const option = optionNth(0);
      if (option === '更新时间') {
        return 'update';
      } else {
        return 'create';
      }
    };
    return runCatching(
      Locator.favoredRepository()
        .listFavoredWenkuNovel(favoredId, {
          page,
          pageSize: 24,
          sort: optionSort(),
        })
        .then((it) => ({ type: 'wenku', ...it })),
    );
  };
});

const showControlPanel = ref(false);

const novelListRef = ref<InstanceType<typeof NovelListWenku>>();
</script>

<template>
  <bookshelf-layout :menu-key="`wenku/${favoredId}`">
    <n-flex style="margin-bottom: 24px">
      <c-button
        label="选择"
        :icon="ChecklistOutlined"
        @action="showControlPanel = !showControlPanel"
      />
      <bookshelf-list-button
        v-if="!isWideScreen"
        :menu-key="`wenku/${favoredId}`"
      />
    </n-flex>

    <n-collapse-transition :show="showControlPanel" style="margin-bottom: 16px">
      <bookshelf-wenku-control
        :selected-novels="novelListRef!!.selectedNovels"
        :favoredId="favoredId"
        @select-all="novelListRef!!.selectAll()"
        @invert-selection="novelListRef!!.invertSelection()"
      />
    </n-collapse-transition>

    <novel-page
      :page="page"
      :selected="selected"
      :loader="loader"
      :options="options"
      loadingType="wenkuNovel"
      v-slot="{ items }"
    >
      <novel-list-wenku
        ref="novelListRef"
        :items="items"
        :selectable="showControlPanel"
        simple
      />
    </novel-page>
  </bookshelf-layout>
</template>
