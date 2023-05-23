<script lang="ts" setup>
import { ref } from 'vue';
import { UploadFileInfo, useMessage } from 'naive-ui';
import { UploadFilled } from '@vicons/material';

import { ResultState } from '@/data/api/result';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const authInfoStore = useAuthInfoStore();
const message = useMessage();

const novelId = 'non-archived';
const nonArchived = ref<ResultState<VolumeJpDto[]>>();

async function refreshList() {
  const result = await ApiWenkuNovel.listNonArchived();
  nonArchived.value = result;
}
refreshList();

function handleFinish({
  file,
  event,
}: {
  file: UploadFileInfo;
  event?: ProgressEvent;
}) {
  refreshList();
  return undefined;
}

async function beforeUpload({ file }: { file: UploadFileInfo }) {
  if (!authInfoStore.token) {
    message.info('请先登录');
    return false;
  }
  if (file.file?.size && file.file.size > 1024 * 1024 * 40) {
    message.error('文件大小不能超过20MB');
    return false;
  }
  if (file.type === 'application/epub+zip') {
    return true;
  } else {
    message.error('只能上传epub格式的文件');
    return false;
  }
}
</script>

<template>
  <MainLayout>
    <n-upload
      multiple
      :headers="{ Authorization: 'Bearer ' + authInfoStore.token }"
      :action="ApiWenkuNovel.createVolumeJpUploadUrl(novelId)"
      :trigger-style="{ width: '100%' }"
      @finish="handleFinish"
      @before-upload="beforeUpload"
    >
      <n-space align="baseline" justify="space-between" style="width: 100">
        <n-h1>Epub翻译</n-h1>
        <n-button>
          <template #icon><n-icon :component="UploadFilled" /></template>
          上传章节
        </n-button>
      </n-space>
    </n-upload>
    <n-p>
      上传日文Epub小说，可以像翻译网络小说一样生成中文Epub。
      如何使用翻译插件请参考
      <n-a href="/how-to-use" target="_blank">使用说明</n-a>。
    </n-p>
    <n-p> 本功能还在测试中，请注意： </n-p>
    <n-ul>
      <n-li>
        Epub的格式千奇百怪，如果生成的中文版有问题，请向我
        <n-a href="/feedback" target="_blank"> 反馈</n-a>。
      </n-li>
      <n-li>性能优化还没做，用的时候请不要太粗暴。</n-li>
      <n-li>因为还在测试，生成的Epub格式可能会有变化。</n-li>
      <n-li>上传的小说是公开的。</n-li>
    </n-ul>

    <template v-if="nonArchived?.ok">
      <div v-for="volume in nonArchived.value" style="padding: 0px">
        <n-h3 class="title" style="margin-bottom: 4px">
          {{ volume.volumeId }}
        </n-h3>
        <WenkuTranslate
          :novel-id="novelId"
          :volume-id="volume.volumeId"
          :total="volume.jp"
          v-model:baidu="volume.baidu"
          v-model:youdao="volume.youdao"
        />
      </div>

      <n-empty v-if="nonArchived.value.length === 0" description="空列表" />
    </template>

    <div v-if="nonArchived && !nonArchived.ok">
      <n-result
        status="error"
        title="加载错误"
        :description="nonArchived.error.message"
      />
    </div>
  </MainLayout>
</template>
