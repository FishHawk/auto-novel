<script lang="ts" setup>
import { ChecklistOutlined } from '@vicons/material';

import { notice } from '@/pages/components/NoticeBoard.vue';
import BookshelfList from './components/BookshelfList.vue';

const showOperationPanel = ref(false);

const bookshelfListRef = ref<InstanceType<typeof BookshelfList>>();

const notices = [notice('本地文件支持EPUB、TXT、SRT文件。')];
</script>

<template>
  <div class="layout-content">
    <n-h1>个人书架</n-h1>

    <notice-board :notices="notices">
      <n-flex>
        <c-a to="/workspace/katakana"> 术语表工作区 </c-a> /
        <c-a to="/workspace/gpt"> GPT工作区 </c-a> /
        <c-a to="/workspace/sakura"> Sakura工作区 </c-a> /
        <c-a to="/workspace/interactive"> 交互翻译 </c-a>
      </n-flex>
    </notice-board>

    <n-flex style="margin-bottom: 24px; margin-top: 12px">
      <div>
        <bookshelf-add-button />
      </div>
      <c-button
        label="选择"
        :icon="ChecklistOutlined"
        @action="showOperationPanel = !showOperationPanel"
      />
    </n-flex>

    <n-collapse-transition
      :show="showOperationPanel"
      style="margin-bottom: 16px"
    >
      <bookshelf-operation-panel
        :selected-ids="bookshelfListRef!!.selectedIds"
        @select-all="bookshelfListRef!!.selectAll()"
        @invert-selection="bookshelfListRef!!.invertSelection()"
      />
    </n-collapse-transition>

    <bookshelf-list ref="bookshelfListRef" :selectable="showOperationPanel" />
  </div>
</template>
