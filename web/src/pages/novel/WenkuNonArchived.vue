<script lang="ts" setup>
import { ref } from 'vue';

import { Err, Ok, ResultState } from '@/data/api/result';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { useUserDataStore } from '@/data/stores/userData';

const userData = useUserDataStore();

const userNovelId = ref('');
const volumesResult = ref<ResultState<VolumeJpDto[]>>();
const volumesUserResult = ref<ResultState<VolumeJpDto[]>>();

async function getVolumesNonArchived() {
  const result = await ApiWenkuNovel.listVolumesNonArchived();
  volumesResult.value = result;
}
getVolumesNonArchived();

async function getVolumesUser() {
  if (userData.logined) {
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
    <n-h1>Epub/Txt翻译</n-h1>
    <n-p>
      上传日文Epub/Txt小说，可以像翻译网络小说一样生成中文版。如何使用翻译插件请参考
      <RouterNA to="/forum/64f3d63f794cbb1321145c07">使用说明</RouterNA>。
      <br />
      Epub的格式千奇百怪，如果生成的中文版有问题，请向我
      <RouterNA to="/forum/64f3e280794cbb1321145c09">反馈</RouterNA>。
    </n-p>

    <SectionHeader title="私人缓存区" />
    <n-p>
      这里上传的小说是不公开的，如果你的小说有放流时间等限制无法公开，可以上传到这里。
      <br />
      上传到这里的小说过一段时间会清理（现在还没做，想清理可以在反馈帖找我手动清）。
    </n-p>
    <UploadButton type="jp" :novelId="userNovelId" />
    <n-divider />

    <ResultView
      :result="volumesUserResult"
      :showEmpty="(it: any) => it.length === 0"
      v-slot="{ value: volumes }"
    >
      <WenkuTranslate :novelId="userNovelId" :volumes="volumes" />
    </ResultView>

    <SectionHeader title="通用缓存区" />
    <n-p>
      这里上传的小说是公开的，我会定期整理到文库版页面。
      <br />
      在上传之前请确定<RouterNA to="/wenku-list">文库列表</RouterNA
      >还没有你的小说页面。如果有的话，请在小说页面上传。
      <br />
      你也可以自己<RouterNA to="/wenku-edit">创建文库小说</RouterNA>。
      <br />
      <b>请正常使用这个功能，有什么特殊的需求请至少先在反馈帖说下。</b>
    </n-p>
    <UploadButton
      type="jp"
      novelId="non-archived"
      @uploadFinished="getVolumesNonArchived()"
    />
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
