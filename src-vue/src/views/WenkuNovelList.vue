<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { onMounted, ref } from 'vue';

import ApiWenkuNovel, { WenkuListPageDto } from '../data/api/api_wenku_novel';
import { ResultState } from '../data/api/result';
import Bangumi from '../data/api/bangumi';
import { useAuthInfoStore } from '../data/stores/authInfo';

const message = useMessage();

const authInfoStore = useAuthInfoStore();

const novelList = ref<ResultState<WenkuListPageDto>>();

onMounted(() => {
  loader();
});

async function loader() {
  const result = await ApiWenkuNovel.list(0, '');
  novelList.value = result;
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
      <n-button @click="openDialog()">创建</n-button>
    </n-space>

    <n-divider />
    <div v-if="novelList?.ok">
      <n-grid x-gap="12" cols="3 400:4">
        <n-grid-item v-for="item in novelList.value.items">
          <n-a :href="`/wenku/${item.bookId}`" target="_blank">
            <n-card size="small" >
              <template #cover>
                <img :src="item.cover" alt="cover" />
              </template>
              <template #header>
                <div
                  style="
                    text-overflow: ellipsis;
                    overflow: hidden;
                    white-space: nowrap;
                  "
                >
                  {{ item.title }}
                </div>
              </template>
            </n-card>
          </n-a>
        </n-grid-item>
      </n-grid>

      <n-empty v-if="novelList.value.items.length === 0" description="空列表" />
    </div>
    <n-result
      v-if="novelList && !novelList.ok"
      status="error"
      title="加载错误"
      :description="novelList.error.message"
    />
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
