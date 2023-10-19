<script lang="ts" setup>
import { ref } from 'vue';

import { Ok, ResultState, mapOk } from '@/data/result';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { useUserDataStore } from '@/data/stores/user_data';

const userData = useUserDataStore();

const novelId = ref('');
const volumesUserResult = ref<ResultState<VolumeJpDto[]>>();

async function getVolumesUser() {
  if (userData.isLoggedIn) {
    const result = await ApiWenkuNovel.listVolumesUser();
    volumesUserResult.value = mapOk(result, (it) => {
      novelId.value = it.novelId;
      return it.list;
    });
  } else {
    volumesUserResult.value = Ok([]);
  }
}
getVolumesUser();
</script>

<template>
  <MainLayout>
    <n-h1>EPUB/TXT文件翻译-私人</n-h1>

    <UploadButton
      v-if="novelId"
      type="jp"
      :novelId="novelId"
      @uploadFinished="getVolumesUser()"
    />
    <n-divider />

    <ResultView
      :result="volumesUserResult"
      :showEmpty="(it: any) => it.length === 0"
      v-slot="{ value: volumes }"
    >
      <WenkuTranslate
        :novelId="novelId"
        :volumes="volumes"
        @deleted="getVolumesUser()"
      />
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
