<script lang="ts" setup>
import { LockOutlined, PlusOutlined, PushPinOutlined } from '@vicons/material';

import { ArticleRepository } from '@/data/api';
import { useUserDataStore } from '@/data/stores/user_data';
import { ArticleCategory, ArticleOutline } from '@/model/Article';
import { Page } from '@/model/Page';
import { doAction } from '@/pages/util';
import { Result, runCatching } from '@/util/result';

const route = useRoute();
const router = useRouter();
const message = useMessage();
const userData = useUserDataStore();

const articleCategoryOptions = [
  { value: 'Guide', label: '使用指南' },
  { value: 'General', label: '小说交流' },
  { value: 'Support', label: '反馈与建议' },
];

const parsePage = (q: typeof route.query) => parseInt(q.page as string) || 1;
const parseCategory = (q: typeof route.query) =>
  (q.category as ArticleCategory) || 'Guide';

const articlePageResult = ref<Result<Page<ArticleOutline>>>();
const category = ref<ArticleCategory>(parseCategory(route.query));
const currentPage = ref(parsePage(route.query));
const pageNumber = ref(1);

watch(category, () => {
  currentPage.value = 1;
});

watch(
  route,
  async (_) => {
    const newPage = currentPage.value;
    const newCategory = category.value;

    articlePageResult.value = undefined;
    const result = await runCatching(
      ArticleRepository.listArticle({
        page: newPage - 1,
        pageSize: 20,
        category: newCategory,
      })
    );
    if (currentPage.value === newPage) {
      articlePageResult.value = result;
      if (result.ok) {
        pageNumber.value = result.value.pageNumber;
      }
    }
  },
  { immediate: true }
);

watch([category, currentPage], (_) => {
  router.push({
    path: route.path,
    query: { page: currentPage.value, category: category.value },
  });
});

const lockArticle = (article: ArticleOutline) =>
  doAction(
    ArticleRepository.lockArticle(article.id).then(
      () => (article.locked = true)
    ),
    '锁定',
    message
  );

const unlockArticle = (article: ArticleOutline) =>
  doAction(
    ArticleRepository.unlockArticle(article.id).then(
      () => (article.locked = false)
    ),
    '解除锁定',
    message
  );

const pinArticle = (article: ArticleOutline) =>
  doAction(
    ArticleRepository.pinArticle(article.id).then(
      () => (article.pinned = true)
    ),
    '置顶',
    message
  );

const unpinArticle = (article: ArticleOutline) =>
  doAction(
    ArticleRepository.unpinArticle(article.id).then(
      () => (article.pinned = false)
    ),
    '解除置顶',
    message
  );

const deleteArticle = (article: ArticleOutline) =>
  doAction(ArticleRepository.deleteArticle(article.id), '删除', message);
</script>

<template>
  <div class="layout-content">
    <n-h1>论坛</n-h1>

    <router-link :to="`/forum-edit?category=${category}`">
      <c-button
        label="发布文章"
        :icon="PlusOutlined"
        style="margin-bottom: 16px"
      />
    </router-link>

    <c-action-wrapper title="版块" style="margin-bottom: 20px">
      <c-radio-group
        v-model:value="category"
        :options="articleCategoryOptions"
      />
    </c-action-wrapper>

    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
    <c-result
      :result="articlePageResult"
      :show-empty="(it: Page<ArticleOutline>) => it.items.length === 0"
      v-slot="{ value: page }"
    >
      <n-table :bordered="false" style="margin-top: 24px">
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
                <c-a :to="`/forum/${article.id}`">
                  <b>{{ article.title }}</b>
                </c-a>
              </div>
              <n-text style="font-size: 12px">
                {{
                  article.updateAt === article.createAt ? '发布' : '更新'
                }}于<n-time :time="article.updateAt * 1000" type="relative" />
                by {{ article.user.username }}
              </n-text>

              <n-flex v-if="userData.asAdmin" style="margin-top: 4px">
                <c-button
                  v-if="article.locked"
                  size="tiny"
                  secondary
                  label="解除锁定"
                  @action="unlockArticle(article)"
                />
                <c-button
                  v-else
                  size="tiny"
                  secondary
                  label="锁定"
                  @action="lockArticle(article)"
                />

                <c-button
                  v-if="article.pinned"
                  size="tiny"
                  secondary
                  label="解除置顶"
                  @action="unpinArticle(article)"
                />
                <c-button
                  v-else
                  size="tiny"
                  secondary
                  label="置顶"
                  @action="pinArticle(article)"
                />

                <c-button
                  size="tiny"
                  secondary
                  label="删除"
                  type="error"
                  @action="deleteArticle(article)"
                />
              </n-flex>
            </td>
            <td class="article-number">
              {{ article.numViews }}/{{ article.numComments }}
            </td>
          </tr>
        </tbody>
      </n-table>
    </c-result>
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
