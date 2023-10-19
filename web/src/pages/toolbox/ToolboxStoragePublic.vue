<script lang="ts" setup>
import { ref } from 'vue';

import { ResultState } from '@/data/result';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';

const volumesResult = ref<ResultState<VolumeJpDto[]>>();
async function getVolumesNonArchived() {
  const result = await ApiWenkuNovel.listVolumesNonArchived();
  volumesResult.value = result;
}
getVolumesNonArchived();
</script>

<template>
  <MainLayout>
    <n-h1><n-text type="error">EPUB/TXT文件翻译(已弃用)</n-text></n-h1>

    <n-p>
      鉴于<RouterNA to="/wenku-list">文库小说</RouterNA>已经上线<RouterNA
        to="/wenku-edit"
        >新建小说</RouterNA
      >的功能，本页面不再有存在意义。
      <br />
      目前本页面的上传功能已关闭，翻译和下载功能仍然可以正常使用。
      <br />
      本页面的小说会被我逐步整理到文库小说里（可能很久），之后本页面将会永久移除。
      <br />
      如果你有什么原因想继续使用这个功能，请在<RouterNA
        to="/forum/64f3e280794cbb1321145c09"
        >反馈帖</RouterNA
      >解释下理由。
    </n-p>

    <n-divider />

    <ResultView
      :result="volumesResult"
      :showEmpty="(it: any) => it.length === 0"
      v-slot="{ value: volumes }"
    >
      <WenkuTranslate novel-id="non-archived" :volumes="volumes" />
    </ResultView>
  </MainLayout>
</template>

<style>
.n-collapse
  .n-collapse-item
  .n-collapse-item__content-wrapper
  .n-collapse-item__content-inner {
  padding-top: 0px;
}
</style>
