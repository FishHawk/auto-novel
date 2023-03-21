<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { EditNoteFilled } from '@vicons/material';

import { ResultState } from '../data/api/result';
import ApiNovel, { BookMetadataDto } from '../data/api/api_novel';
import { addHistory } from '../data/history';
import { buildMetadataUrl } from '../data/provider';

import { errorToString } from '../data/handle_error';

const route = useRoute();
const providerId = route.params.providerId as string;
const bookId = route.params.bookId as string;
const url = buildMetadataUrl(providerId, bookId);

const bookMetadata = ref<ResultState<BookMetadataDto>>();

onMounted(() => getMetadata());
async function getMetadata() {
  const result = await ApiNovel.getMetadata(providerId, bookId);
  bookMetadata.value = result;
  if (result.ok) {
    document.title = result.value.titleJp;
    addHistory({ url, title: result.value.titleJp });
  }
}

const showModal = ref(false);
</script>

<template>
  <MainLayout>
    <div v-if="bookMetadata?.ok">
      <n-h2 prefix="bar">
        <n-a :href="url" target="_blank">{{ bookMetadata.value.titleJp }}</n-a>
        <br />
        <span style="color: grey">{{ bookMetadata.value.titleZh }}</span>
      </n-h2>

      <n-p v-if="bookMetadata.value.authors.length > 0">
        作者：
        <span v-for="author in bookMetadata.value.authors">
          <n-a :href="author.link" target="_blank">{{ author.name }}</n-a>
        </span>
      </n-p>

      <n-p>
        <n-space>
          <span>浏览次数:{{ bookMetadata.value.visited }}</span>
          <span>下载次数:{{ bookMetadata.value.downloaded }}</span>
        </n-space>
      </n-p>

      <n-a :href="`/novel-edit/${providerId}/${bookId}`">
        <n-button>
          <template #icon>
            <n-icon> <EditNoteFilled /> </n-icon>
          </template>
          编辑
        </n-button>
      </n-a>

      <n-p>{{ bookMetadata.value.introductionJp }}</n-p>
      <n-p v-if="bookMetadata.value.introductionZh !== undefined">{{
        bookMetadata.value.introductionZh
      }}</n-p>

      <n-h2 prefix="bar">翻译</n-h2>
      <n-p>
        如果需要自定义更新范围，请使用
        <n-a @click="showModal = true">高级模式</n-a>
        。如果要编辑术语表，请进入
        <n-a :href="`/novel-edit/${providerId}/${bookId}`">编辑</n-a>
        界面。
      </n-p>
      <n-p v-if="Object.keys(bookMetadata.value.glossary).length">
        <n-collapse>
          <n-collapse-item title="术语表">
            <table style="border-spacing: 16px 0px">
              <tr v-for="(termZh, termJp) in bookMetadata.value.glossary">
                <td>{{ termJp }}</td>
                <td>=></td>
                <td>{{ termZh }}</td>
              </tr>
            </table>
          </n-collapse-item>
        </n-collapse>
      </n-p>
      <TranslatePanel
        :provider-id="providerId"
        :book-id="bookId"
        v-model:showModal="showModal"
      />

      <n-h2 prefix="bar">目录</n-h2>
      <table style="width: 100%">
        <template v-for="token in bookMetadata.value.toc">
          <n-a
            v-if="token.episodeId"
            :href="`/novel/${providerId}/${bookId}/${token.episodeId}`"
            role="row"
            style="display: table-row"
          >
            <TocItem :token="token" />
          </n-a>
          <tr v-else>
            <TocItem :token="token" />
          </tr>
          <n-divider class="on-desktop" style="width: 200%; margin: 0px" />
          <n-divider class="on-mobile" style="width: 100%; margin: 0px" />
        </template>
      </table>

      <CommentList :post-id="route.path" />
    </div>

    <div v-if="bookMetadata && !bookMetadata.ok">
      <n-result
        status="error"
        title="加载错误"
        :description="errorToString(bookMetadata.error)"
      />
    </div>
  </MainLayout>
</template>
