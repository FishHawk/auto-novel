<script lang="ts" setup>
import { ChecklistOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { WebNovelOutlineDto } from '@/model/WebNovel';
import { runCatching } from '@/util/result';

import { useIsWideScreen } from '@/pages/util';
import NovelListWeb from '../list/components/NovelListWeb.vue';
import { Loader } from '../list/components/NovelPage.vue';

const props = defineProps<{
  page: number;
  selected: number[];
  favoredId: string;
}>();

const isWideScreen = useIsWideScreen();

const { setting } = Locator.settingRepository();

const options = computed(() => {
  return [
    {
      label: '排序',
      tags: setting.value.favoriteCreateTimeFirst
        ? ['收藏时间', '更新时间']
        : ['更新时间', '收藏时间'],
    },
  ];
});

const loader = computed<Loader<WebNovelOutlineDto>>(() => {
  const { favoredId } = props;
  return (page, _query, selected) => {
    const optionNth = (n: number): string => options.value[n].tags[selected[n]];
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
        .listFavoredWebNovel(favoredId, {
          page,
          pageSize: 30,
          sort: optionSort(),
        })
        .then((it) => ({ type: 'web', ...it })),
    );
  };
});

const showControlPanel = ref(false);

const novelListRef = ref<InstanceType<typeof NovelListWeb>>();
</script>

<template>
  <bookshelf-layout :menu-key="`web/${favoredId}`">
    <n-flex style="margin-bottom: 24px">
      <c-button
        label="选择"
        :icon="ChecklistOutlined"
        @action="showControlPanel = !showControlPanel"
      />
      <bookshelf-list-button
        v-if="!isWideScreen"
        :menu-key="`web/${favoredId}`"
      />
    </n-flex>

    <n-collapse-transition :show="showControlPanel" style="margin-bottom: 16px">
      <bookshelf-web-control
        :selected-novels="novelListRef!.selectedNovels"
        :favored-id="favoredId"
        @select-all="novelListRef!.selectAll()"
        @invert-selection="novelListRef!.invertSelection()"
      />
    </n-collapse-transition>

    <novel-page
      :page="page"
      :selected="selected"
      :loader="loader"
      :options="options"
      v-slot="{ items }"
    >
      <novel-list-web
        ref="novelListRef"
        :items="items"
        :selectable="showControlPanel"
        :simple="!setting.showTagInWebFavored"
      />
    </novel-page>
  </bookshelf-layout>
</template>
