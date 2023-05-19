<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import ApiWenkuNovel from '@/data/api/api_wenku_novel';
import { useAuthInfoStore, atLeastMaintainer } from '@/data/stores/authInfo';
import { mapOk } from '@/data/api/result';

import { Loader } from './components/BookList.vue';

const message = useMessage();

const authInfoStore = useAuthInfoStore();

const loader: Loader = (page: number, query: string, _selected: number[]) => {
  return ApiWenkuNovel.list(page - 1, query).then((result) =>
    mapOk(result, (page) => ({ type: 'wenku', page }))
  );
};

const showModal = ref(false);
const bangumiUrl = ref('');

async function importMetadataFromBangumi(url: string) {
  const bookId = /bangumi\.tv\/subject\/([0-9]+)/.exec(url)?.[1];
  if (!bookId) {
    return message.error('链接格式错误');
  }
  const metadataResult = await ApiWenkuNovel.getMetadataFromBangumi(bookId);
  if (metadataResult.ok) {
    const token = authInfoStore.token;
    if (!token) return message.info('请先登录');
    const result = await ApiWenkuNovel.postMetadata(
      metadataResult.value,
      token
    );
    if (result.ok) {
      message.success('创建成功');
    } else {
      message.error('创建失败:' + result.error.message);
    }
  } else {
    message.error('无法从Bangumi获得数据:' + metadataResult.error.message);
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
      <n-space>
        <n-button
          v-if="atLeastMaintainer(authInfoStore.role)"
          @click="openDialog()"
        >
          创建
        </n-button>
        <n-a href="/wenku/non-archived" target="_blank">
          <n-button> 翻译Epub </n-button>
        </n-a>
      </n-space>
    </n-space>
    <BookList :search="true" :options="[]" :loader="loader" />
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
