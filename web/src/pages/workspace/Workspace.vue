<script lang="ts" setup>
import { notice } from '@/pages/components/NoticeBoard.vue';
const notices = [notice('本地文件支持EPUB、TXT、SRT文件。')];
</script>

<template>
  <div class="layout-content">
    <n-h1>工作区</n-h1>

    <notice-board :notices="notices">
      <n-flex>
        <c-a to="/workspace/katakana"> 术语表工作区 </c-a> /
        <c-a to="/workspace/gpt"> GPT工作区 </c-a> /
        <c-a to="/workspace/sakura"> Sakura工作区 </c-a> /
        <c-a to="/workspace/sakura-shared"> Sakura公用队列 </c-a> /
        <c-a to="/workspace/interactive"> 交互翻译 </c-a>
      </n-flex>
    </notice-board>

    <local-volume-list-composite />
  </div>
</template>
