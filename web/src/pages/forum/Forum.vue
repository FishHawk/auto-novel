<script lang="ts" setup>
import {
  LockOutlined,
  MoreVertOutlined,
  PlusOutlined,
  PushPinOutlined,
} from '@vicons/material';
import { useMessage } from 'naive-ui';
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ApiArticle, ArticleOutline } from '@/data/api/api_article';
import { Page } from '@/data/api/common';
import { Result, ResultState } from '@/data/result';
import { useUserDataStore } from '@/data/stores/user_data';

const route = useRoute();
const router = useRouter();
const message = useMessage();
const userData = useUserDataStore();

const parsePage = (q: typeof route.query) =>
  parseInt(route.query.page as string) || 1;

const articlePageResult = ref<ResultState<Page<ArticleOutline>>>();
const currentPage = ref(parsePage(route.query));
const pageNumber = ref(1);

watch(
  route,
  async (route) => {
    const newPage = parsePage(route.query);
    if (newPage !== currentPage.value) {
      currentPage.value = newPage;
    }
    articlePageResult.value = undefined;
    const result = await ApiArticle.listArticle({
      page: currentPage.value - 1,
      pageSize: 20,
    });
    if (currentPage.value === newPage) {
      articlePageResult.value = result;
      if (result.ok) {
        pageNumber.value = result.value.pageNumber;
      }
    }
  },
  { immediate: true }
);

watch(currentPage, (_) => {
  router.push({ path: route.path, query: { page: currentPage.value } });
});

function generateOptions(article: ArticleOutline) {
  const options = [{ label: '删除文章', key: 'delete' }];
  if (article.locked) {
    options.unshift({ label: '解除锁定', key: 'unlock' });
  } else {
    options.unshift({ label: '锁定文章', key: 'lock' });
  }
  if (article.pinned) {
    options.unshift({ label: '解除置顶', key: 'unpin' });
  } else {
    options.unshift({ label: '置顶文章', key: 'pin' });
  }
  return options;
}

async function handleSelect(key: string | number, article: ArticleOutline) {
  function notifyResult<T>(result: Result<T>, label: string) {
    if (result.ok) {
      currentPage.value = currentPage.value;
      message.info(label + '成功');
    } else {
      message.info(label + '失败' + result.error.message);
    }
  }
  if (key === 'lock') {
    const result = await ApiArticle.lockArticle(article.id);
    if (result.ok) article.locked = true;
    notifyResult(result, '锁定');
  } else if (key === 'unlock') {
    const result = await ApiArticle.unlockArticle(article.id);
    if (result.ok) article.locked = false;
    notifyResult(result, '解除锁定');
  } else if (key === 'pin') {
    const result = await ApiArticle.pinArticle(article.id);
    if (result.ok) article.pinned = true;
    notifyResult(result, '置顶');
  } else if (key === 'unpin') {
    const result = await ApiArticle.unpinArticle(article.id);
    if (result.ok) article.pinned = false;
    notifyResult(result, '解除置顶');
  } else if (key === 'delete') {
    const result = await ApiArticle.deleteArticle(article.id);
    notifyResult(result, '删除');
  }
}
</script>

<template>
  <div class="layout-content">
    <n-h1>论坛</n-h1>

    <router-link to="/forum-edit">
      <c-button
        label="发布文章"
        :icon="PlusOutlined"
        style="margin-bottom: 16px"
      />
    </router-link>

    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
    <ResultView
      :result="articlePageResult"
      :showEmpty="(it: Page<ArticleOutline>) => it.items.length === 0"
      v-slot="{ value: page }"
    >
      <n-table :bordered="false">
        <thead>
          <tr>
            <th><b>标题</b></th>
            <th class="article-number"><b>查看/回复</b></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="article of page.items">
            <td>
              <div>
                <n-icon
                  v-if="article.pinned"
                  size="15"
                  :component="PushPinOutlined"
                  style="vertical-align: middle; margin-bottom: 4px"
                />
                <n-icon
                  v-if="article.locked"
                  size="15"
                  :component="LockOutlined"
                  style="vertical-align: middle; margin-bottom: 4px"
                />
                <RouterNA :to="`/forum/${article.id}`">
                  <b>{{ article.title }}</b>
                </RouterNA>
              </div>
              <n-text style="font-size: 12px">
                {{
                  article.updateAt === article.createAt ? '发布' : '更新'
                }}于<n-time :time="article.updateAt * 1000" type="relative" />
                by {{ article.user.username }}
              </n-text>
            </td>
            <td class="article-number">
              {{ article.numViews }}/{{ article.numComments }}
              <br/>
              <n-dropdown
                v-if="userData.asAdmin"
                trigger="click"
                :options="generateOptions(article)"
                @select="(key: any) => handleSelect(key, article)"
              >
                <n-button circle size="tiny">
                  <n-icon :component="MoreVertOutlined" />
                </n-button>
              </n-dropdown>
            </td>
          </tr>
        </tbody>
      </n-table>
    </ResultView>
    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
      style="margin-top: 20px"
    />
  </div>
</template>

<style scoped>
.article-number {
  width: 50px;
  text-align: center;
}
</style>
