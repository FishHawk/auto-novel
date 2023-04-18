<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import ApiWenkuNovel from '@/data/api/api_wenku_novel';
import Bangumi from '@/data/api/bangumi';
import { useAuthInfoStore, atLeastMaintainer } from '@/data/stores/authInfo';

const message = useMessage();

const authInfoStore = useAuthInfoStore();

async function loader(page: number, query: string, selected: number[]) {
  return ApiWenkuNovel.list(page - 1, query);
}

const showModal = ref(false);
const bangumiUrl = ref('');

async function importMetadataFromBangumi(url: string) {
  const bookId = /bangumi\.tv\/subject\/([0-9]+)/.exec(url)?.[1];
  if (!bookId) {
    return message.error('链接格式错误');
  }
  const sectionResult = await Bangumi.getSection(bookId);
  if (sectionResult.ok) {
    const token = authInfoStore.token;
    if (!token) return message.info('请先登录');

    const metadata = {
      bookId,
      title: sectionResult.value.name_cn,
      cover: sectionResult.value.images.medium,
      coverSmall: sectionResult.value.images.small,
      author: '',
      artist: '',
      keywords: sectionResult.value.tags.map((it) => it.name),
      introduction: sectionResult.value.summary,
    };
    sectionResult.value.infobox.forEach((it) => {
      if (it.key == '作者') {
        metadata.author = it.value;
      } else if (it.key == '插图') {
        metadata.artist = it.value;
      }
    });
    const result = await ApiWenkuNovel.postMetadata(metadata, token);
    if (result.ok) {
      message.success('创建成功');
    } else {
      message.error('创建失败:' + result.error.message);
    }
  } else {
    message.error('无法从Bangumi获得数据:' + sectionResult.error.message);
  }
}

function openDialog() {
  if (!authInfoStore.token) return message.info('请先登录');
  showModal.value = true;
}
</script>

<template>
  <ListLayout>
    <n-space align="baseline" justify="space-between" style="width: 100">
      <n-h1>文库小说</n-h1>
      <n-button
        v-if="atLeastMaintainer(authInfoStore.role)"
        @click="openDialog()"
        >创建</n-button
      >
    </n-space>
    <WenkuBookList :search="true" :options="[]" :loader="loader" />
  </ListLayout>

  <n-modal v-model:show="showModal">
    <n-card
      style="width: min(400px, calc(100% - 16px))"
      title="创建"
      :bordered="false"
      size="huge"
      role="dialog"
      aria-modal="true"
    >
      <n-p>
        从
        <n-a href="https://bangumi.tv/" target="_blank">Bangumi</n-a>
        导入元数据来创建书。链接示例: https://bangumi.tv/subject/1177
      </n-p>

      <n-input-group>
        <n-input
          v-model:value="bangumiUrl"
          size="large"
          placeholder="请输入Bangumi链接..."
          @keyup.enter="importMetadataFromBangumi(bangumiUrl)"
        />
        <n-button
          size="large"
          type="primary"
          @click="importMetadataFromBangumi(bangumiUrl)"
        >
          导入
        </n-button>
      </n-input-group>
    </n-card>
  </n-modal>
</template>

<style scoped>
.n-card-header__main {
  text-overflow: ellipsis;
}
</style>
