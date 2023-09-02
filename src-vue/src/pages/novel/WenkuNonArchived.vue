<script lang="ts" setup>
import { ref } from 'vue';

import { Err, Ok, ResultState } from '@/data/api/result';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const authInfoStore = useAuthInfoStore();

const userNovelId = ref('');
const volumesResult = ref<ResultState<VolumeJpDto[]>>();
const volumesUserResult = ref<ResultState<VolumeJpDto[]>>();

async function getVolumesNonArchived() {
  const result = await ApiWenkuNovel.listVolumesNonArchived();
  volumesResult.value = result;
}
getVolumesNonArchived();

async function getVolumesUser() {
  const token = authInfoStore.token;
  if (token) {
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
      <n-a href="/wiki/extension" target="_blank">使用说明</n-a>。
      <br />
      Epub的格式千奇百怪，如果生成的中文版有问题，请向我
      <n-a href="/wiki/feedback" target="_blank">反馈</n-a>。
    </n-p>

    <SectionHeader title="私人缓存区" />
    <n-p>
      这里上传的小说是不公开的，如果你的小说有放流时间等限制无法公开，可以上传到这里。
      <br />
      上传到这里的小说过一段时间会清理（现在还没做）。
    </n-p>
    <UploadButton type="jp" :novelId="userNovelId" />
    <ResultView
      :result="volumesUserResult"
      :showEmpty="(it: any) => it.length === 0"
      v-slot="{ value: volumes }"
    >
      <n-collapse
        display-directive="show"
        :theme-overrides="{
          itemMargin: '0px',
          titlePadding: '0px',
        }"
        style="margin-top: 16px"
      >
        <n-collapse-item
          v-for="volume in volumes"
          :title="volume.volumeId"
          style="padding: 4px"
        >
          <WenkuTranslateItem
            :novelId="userNovelId"
            :volume-id="volume.volumeId"
            :total="volume.total"
            v-model:baidu="volume.baidu"
            v-model:youdao="volume.youdao"
            v-model:gpt="volume.gpt"
            style="padding-top: -8px"
          />
        </n-collapse-item>
      </n-collapse>
    </ResultView>

    <SectionHeader title="通用缓存区" />
    <n-p>
      这里上传的小说是公开的，我会定期整理到文库版页面。
      <br />
      <b>
        在上传之前请确定文库列表还没有你的小说页面。如果有的话，请在小说页面上传。
      </b>
    </n-p>
    <UploadButton
      type="jp"
      novelId="non-archived"
      @uploadFinished="getVolumesNonArchived()"
    />
    <ResultView
      :result="volumesResult"
      :showEmpty="(it: any) => it.length === 0"
      v-slot="{ value: volumes }"
    >
      <n-collapse
        display-directive="show"
        :theme-overrides="{
          itemMargin: '0px',
          titlePadding: '0px',
        }"
        style="margin-top: 16px"
      >
        <n-collapse-item
          v-for="volume in volumes"
          :title="volume.volumeId"
          style="padding: 4px"
        >
          <WenkuTranslateItem
            novelId="non-archived"
            :volume-id="volume.volumeId"
            :total="volume.total"
            v-model:baidu="volume.baidu"
            v-model:youdao="volume.youdao"
            v-model:gpt="volume.gpt"
            style="padding-top: -8px"
          />
        </n-collapse-item>
      </n-collapse>
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
