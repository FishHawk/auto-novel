<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { UploadFileInfo, useMessage } from 'naive-ui';
import { UploadFilled } from '@vicons/material';

import { ResultState } from '../../data/api/result';
import ApiWenkuNovel, {
  WenkuMetadataDto,
} from '../../data/api/api_wenku_novel';
import {
  useAuthInfoStore,
  atLeastMaintainer,
} from '../../data/stores/authInfo';

const authInfoStore = useAuthInfoStore();

const message = useMessage();

const route = useRoute();
const bookId = route.params.bookId as string;

const novelMetadata = ref<ResultState<WenkuMetadataDto>>();

onMounted(() => getMetadata());
async function getMetadata() {
  const result = await ApiWenkuNovel.getMetadata(bookId);
  novelMetadata.value = result;
  if (result.ok) {
    document.title = result.value.title;
  }
}

async function refreshMetadata() {
  const result = await ApiWenkuNovel.getMetadata(bookId);
  if (result.ok) {
    novelMetadata.value = result;
  }
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
  if (file.type === 'application/epub+zip' || file.type === 'text/plain') {
    return true;
  } else {
    message.error('只能上传epub或txt格式的文件');
    return false;
  }
}
function handleFinish({
  file,
  event,
}: {
  file: UploadFileInfo;
  event?: ProgressEvent;
}) {
  refreshMetadata();
  return undefined;
}

async function updateMetadata() {
  const metadataResult = await ApiWenkuNovel.getMetadataFromBangumi(bookId);
  if (metadataResult.ok) {
    const token = authInfoStore.token;
    if (!token) return message.info('请先登录');
    const result = await ApiWenkuNovel.putMetadata(metadataResult.value, token);
    if (result.ok) {
      message.success('更新成功');
    } else {
      message.error('更新失败:' + result.error.message);
    }
  } else {
    message.error('无法从Bangumi获得数据:' + metadataResult.error.message);
  }
}
</script>

<template>
  <MainLayout>
    <template v-slot:full-width>
      <div
        v-if="novelMetadata?.ok"
        :style="{
          background:
            'linear-gradient( to top, rgba(255, 255, 255, 1), rgba(255, 255, 255, 0.7)), ' +
            `url(${novelMetadata.value.cover})`,
        }"
        style="
          position: absolute;
          left: 0;
          right: 0;
          height: 300px;
          clip: rect(0, auto, auto, 0);
          background-size: cover;
          background-position: center 25%;
        "
      />
    </template>

    <div v-if="novelMetadata?.ok">
      <n-space :wrap="false" style="margin-top: 40px; min-height: 260px">
        <n-card size="small" style="width: 160px">
          <template #cover>
            <img :src="novelMetadata.value.cover" alt="cover" />
          </template>
        </n-card>
        <div>
          <n-h1 prefix="bar" style="font-size: 22px; font-weight: 900">
            {{
              novelMetadata.value.titleCn
                ? novelMetadata.value.titleCn
                : novelMetadata.value.title
            }}
          </n-h1>

          <table style="border-spacing: 0px 8px">
            <TagGroup
              v-if="novelMetadata.value.author"
              label="作者"
              :tags="[novelMetadata.value.author]"
            />
            <TagGroup
              v-if="novelMetadata.value.artist"
              label="插图"
              :tags="[novelMetadata.value.artist]"
            />
            <TagGroup
              v-if="novelMetadata.value.keywords.length > 0"
              class="on-desktop"
              label="标签"
              :tags="novelMetadata.value.keywords"
            />
          </table>
        </div>
      </n-space>

      <n-button
        v-if="atLeastMaintainer(authInfoStore.role)"
        @click="updateMetadata()"
      >
        <template #icon><n-icon :component="UploadFilled" /></template>
        更新元数据
      </n-button>

      <n-p>原名：{{ novelMetadata.value.title }}</n-p>
      <n-p
        v-html="novelMetadata.value.introduction.replace(/\r\n/g, '<br />')"
      />

      <div class="on-mobile">
        <n-space :size="[4, 4]">
          <n-tag
            v-for="tag of novelMetadata.value.keywords"
            :bordered="false"
            size="small"
          >
            {{ tag }}
          </n-tag>
        </n-space>
      </div>

      <n-upload
        multiple
        :headers="{ Authorization: 'Bearer ' + authInfoStore.token }"
        :action="ApiWenkuNovel.createUploadUrl(bookId)"
        :trigger-style="{ width: '100%' }"
        @finish="handleFinish"
        @before-upload="beforeUpload"
      >
        <n-space align="baseline" justify="space-between" style="width: 100">
          <n-h2 prefix="bar">目录</n-h2>
          <n-button v-if="atLeastMaintainer(authInfoStore.role)">
            <template #icon><n-icon :component="UploadFilled" /></template>
            上传章节
          </n-button>
        </n-space>
      </n-upload>

      <n-ul>
        <n-li
          v-for="fileName in novelMetadata.value.files.sort((a, b) =>
            a.localeCompare(b)
          )"
        >
          <n-a
            :href="`/files-wenku/${novelMetadata.value.bookId}/${fileName}`"
            target="_blank"
            :download="fileName"
          >
            {{ fileName }}
          </n-a>
        </n-li>
      </n-ul>

      <n-empty
        v-if="novelMetadata.value.files.length === 0"
        description="空列表"
      />

      <CommentList :post-id="route.path" />
    </div>

    <div v-if="novelMetadata && !novelMetadata.ok">
      <n-result
        status="error"
        title="加载错误"
        :description="novelMetadata.error.message"
      />
    </div>
  </MainLayout>
</template>
