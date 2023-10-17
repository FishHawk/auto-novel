<script lang="ts" setup>
import { ref } from 'vue';

import { Err, Ok, ResultState } from '@/data/api/result';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { useUserDataStore } from '@/data/stores/userData';

const userData = useUserDataStore();

const userNovelId = ref('');
const volumesUserResult = ref<ResultState<VolumeJpDto[]>>();

async function getVolumesUser() {
  if (userData.isLoggedIn) {
    const result = await ApiWenkuNovel.listVolumesUser();
    if (result.ok) {
      volumesUserResult.value = Ok(result.value.list);
      userNovelId.value = result.value.novelId;
    } else {
      volumesUserResult.value = Err(result.error);
    }
  } else {
    volumesUserResult.value = Ok([]);
  }
}
getVolumesUser();
</script>

<template>
  <MainLayout>
    <n-h1>EPUB/TXT文件翻译-私人</n-h1>

    <UploadButton type="jp" :novelId="userNovelId" />
    <n-divider />

    <ResultView
      :result="volumesUserResult"
      :showEmpty="(it: any) => it.length === 0"
      v-slot="{ value: volumes }"
    >
      <WenkuTranslate :novelId="userNovelId" :volumes="volumes" />
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
