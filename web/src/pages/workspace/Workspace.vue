<script lang="ts" setup>
import { notice } from '@/pages/components/NoticeBoard.vue';
const notices = [
  notice(
    '本地文件支持EPUB、TXT、SRT文件。'
  ),
];
</script>

<template>
  <div class="layout-content">
    <n-h1>工作区</n-h1>

    <notice-board :notices="notices">
      <n-flex>
        <RouterNA to="/workspace/katakana"> 术语表工作区 </RouterNA> /
        <RouterNA to="/workspace/gpt"> GPT工作区 </RouterNA> /
        <RouterNA to="/workspace/sakura"> Sakura工作区 </RouterNA> /
        <RouterNA to="/workspace/interactive"> 交互翻译 </RouterNA>
      </n-flex>
    </notice-board>

    <local-volume-list-composite />
  </div>
</template>
