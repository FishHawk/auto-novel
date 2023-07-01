<script lang="ts" setup>
import { ref } from 'vue';
import { UploadFileInfo, useMessage } from 'naive-ui';
import { UploadFilled } from '@vicons/material';
import { createReusableTemplate } from '@vueuse/core';

import { Err, Ok, ResultState } from '@/data/api/result';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const [DefineVolumeList, ReuseVolumeList] = createReusableTemplate<{
  volumes: ResultState<VolumeJpDto[]>;
  novelId: string;
}>();

const authInfoStore = useAuthInfoStore();
const message = useMessage();

const volumes = ref<ResultState<VolumeJpDto[]>>();
const userNovelId = ref('');
const volumesUser = ref<ResultState<VolumeJpDto[]>>();

async function getVolumesNonArchived() {
  const result = await ApiWenkuNovel.listVolumesNonArchived();
  volumes.value = result;
}
getVolumesNonArchived();

async function getVolumesUser() {
  const token = authInfoStore.token;
  if (token) {
    const result = await ApiWenkuNovel.listVolumesUser(token);
    if (result.ok) {
      volumesUser.value = Ok(result.value.list);
      userNovelId.value = result.value.novelId;
    } else {
      volumesUser.value = Err(result.error);
    }
  } else {
    volumesUser.value = Ok([]);
  }
}
getVolumesUser();

function handleFinish({
  file,
  event,
}: {
  file: UploadFileInfo;
  event?: ProgressEvent;
}) {
  getVolumesUser();
  getVolumesNonArchived();
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
</script>

<template>
  <MainLayout>
    <n-h1>Epub/Txt翻译</n-h1>
    <n-p>
      上传日文Epub/Txt小说，可以像翻译网络小说一样生成中文版。如何使用翻译插件请参考
      <n-a href="/how-to-use" target="_blank">使用说明</n-a>。
    </n-p>
    <n-p> 本功能还在测试中，请注意： </n-p>
    <n-ul>
      <n-li>是Epub还是Txt是用后缀名区分的。</n-li>
      <n-li>
        Epub的格式千奇百怪，如果生成的中文版有问题，请向我
        <n-a href="/feedback" target="_blank">反馈</n-a>。
      </n-li>
      <n-li>
        <b>现在生成的Epub很多是损坏的</b>
        ，可以用Sigil打开再保存来修复（别的类似的打开再保存的方法也行）。具体原因有待测试。
      </n-li>
    </n-ul>

    <DefineVolumeList v-slot="{ volumes, novelId }">
      <n-upload
        multiple
        :headers="{ Authorization: 'Bearer ' + authInfoStore.token }"
        :action="ApiWenkuNovel.createVolumeJpUploadUrl(novelId)"
        @finish="handleFinish"
        @before-upload="beforeUpload"
      >
        <n-button>
          <template #icon><n-icon :component="UploadFilled" /></template>
          上传章节
        </n-button>
      </n-upload>

      <template v-if="volumes?.ok">
        <div v-for="volume in volumes.value" style="padding: 0px">
          <n-h3 class="title" style="margin-bottom: 4px">
            {{ volume.volumeId }}
          </n-h3>
          <WenkuTranslate
            :novel-id="novelId"
            :volume-id="volume.volumeId"
            :total="volume.total"
            v-model:baidu="volume.baidu"
            v-model:youdao="volume.youdao"
          />
        </div>
        <n-empty v-if="volumes.value.length === 0" description="空列表" />
      </template>

      <div v-if="volumes && !volumes.ok">
        <n-result
          status="error"
          title="加载错误"
          :description="volumes.error.message"
        />
      </div>
    </DefineVolumeList>

    <SectionHeader title="私人缓存区" />
    <n-p>
      这里上传的小说是不公开的，如果你的小说因为放流时间的限制，可以上传到这里。
      <br />
      上传到这里的小说过一段时间会清理（现在还没做）。
    </n-p>
    <ReuseVolumeList :volumes="volumesUser" :novelId="userNovelId" />

    <SectionHeader title="通用缓存区" />
    <n-p>
      这里上传的小说是公开的，如果你找不到对应的小说页面（现在还没做），可以上传到这里。
      <br />
      上传到这里的小说过一段时间会移动到对应的文库版页面里面（现在还没做）。
    </n-p>
    <ReuseVolumeList :volumes="volumes" novelId="non-archived" />
  </MainLayout>
</template>
