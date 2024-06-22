<script lang="ts" setup>
import { ChecklistOutlined } from '@vicons/material';

import { useIsWideScreen } from '@/pages/util';
import BookshelfList from './components/BookshelfList.vue';

defineProps<{
  favoredId: string;
}>();

const isWideScreen = useIsWideScreen(850);

const showControlPanel = ref(false);

const bookshelfListRef = ref<InstanceType<typeof BookshelfList>>();
</script>

<template>
  <bookshelf-layout :menu-key="`local/${favoredId}`">
    <n-h1>我的收藏：本地小说</n-h1>

    <n-flex style="margin-bottom: 24px">
      <c-button
        label="选择"
        :icon="ChecklistOutlined"
        @action="showControlPanel = !showControlPanel"
      />
      <bookshelf-list-button
        v-if="!isWideScreen"
        :menu-key="`local/${favoredId}`"
      />
      <div>
        <bookshelf-local-add-button />
      </div>
    </n-flex>

    <n-collapse-transition :show="showControlPanel" style="margin-bottom: 16px">
      <bookshelf-local-control
        :selected-ids="bookshelfListRef!!.selectedIds"
        :favoredId="favoredId"
        @select-all="bookshelfListRef!!.selectAll()"
        @invert-selection="bookshelfListRef!!.invertSelection()"
      />
    </n-collapse-transition>

    <bookshelf-local-list
      ref="bookshelfListRef"
      :favoredId="favoredId"
      :selectable="showControlPanel"
    />
  </bookshelf-layout>
</template>
