<script lang="ts" setup>
import { UploadFileInfo, useMessage } from 'naive-ui';
import { UploadFilled } from '@vicons/material';

import { ResultState } from '@/data/api/result';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const { novelId, type, volumesResult } = defineProps<{
  novelId: string;
  type: 'jp' | 'zh';
  volumesResult: ResultState<VolumeJpDto[] | string[]>;
}>();

const emits = defineEmits<{ uploadFinished: [] }>();

const authInfoStore = useAuthInfoStore();
const message = useMessage();

function sortVolumesJp(volumes: VolumeJpDto[]) {
  return volumes.sort((a, b) => a.volumeId.localeCompare(b.volumeId));
}

function sortVolumesZh(volumes: string[]) {
  return volumes.sort((a, b) => a.localeCompare(b));
}

function handleFinish({
  file,
  event,
}: {
  file: UploadFileInfo;
  event?: ProgressEvent;
}) {
  emits('uploadFinished');
  return undefined;
}

async function beforeUpload({ file }: { file: UploadFileInfo }) {
  if (!authInfoStore.token) {
    message.info('请先登录');
    return false;
  }
  if (file.file?.size && file.file.size > 1024 * 1024 * 40) {
    message.error('文件大小不能超过40MB');
    return false;
  }
}

function createUploadUrl(novelId: string): string {
  if (type === 'jp') {
    return ApiWenkuNovel.createVolumeJpUploadUrl(novelId);
  } else {
    return ApiWenkuNovel.createVolumeZhUploadUrl(novelId);
  }
}
</script>

<template>
  <n-upload
    multiple
    :headers="{ Authorization: 'Bearer ' + authInfoStore.token }"
    :action="createUploadUrl(novelId)"
    @finish="handleFinish"
    @before-upload="beforeUpload"
  >
    <n-button>
      <template #icon><n-icon :component="UploadFilled" /></template>
      上传章节
    </n-button>
  </n-upload>

  <ResultView
    :result="volumesResult"
    :showEmpty="(it: any) => it.length === 0"
    v-slot="{ value: volumes }"
  >
    <n-ul v-if="type === 'zh'">
      <n-li v-for="fileName in sortVolumesZh(volumes as string[])">
        <n-a
          :href="`/files-wenku/${novelId}/${fileName}`"
          target="_blank"
          :download="fileName"
        >
          {{ fileName }}
        </n-a>
      </n-li>
    </n-ul>

    <n-collapse
      v-else
      display-directive="show"
      :theme-overrides="{
        itemMargin: '0px',
        titlePadding: '0px',
      }"
      style="margin-top: 16px"
    >
      <n-collapse-item
        v-for="volume in sortVolumesJp(volumes as VolumeJpDto[])"
        :title="volume.volumeId"
        style="padding: 4px"
      >
        <WenkuTranslate
          :novel-id="novelId"
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
</template>
<style>
.n-collapse
  .n-collapse-item
  .n-collapse-item__content-wrapper
  .n-collapse-item__content-inner {
  padding-top: 0px;
}
</style>
