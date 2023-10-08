<script lang="ts" setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';

import { ApiArticle, Article } from '@/data/api/api_article';
import { ResultState } from '@/data/api/result';
import { useUserDataStore } from '@/data/stores/userData';

const route = useRoute();
const userData = useUserDataStore();

const articleId = route.params.id as string;
const articleResult = ref<ResultState<Article>>();

onMounted(async () => {
  const result = await ApiArticle.getArticle(articleId);
  articleResult.value = result;
  if (result.ok) {
    document.title = result.value.title;
  }
});
</script>

<template>
  <MainLayout>
    <ResultView
      :result="articleResult"
      :showEmpty="(it: Article) => false"
      v-slot="{ value: article }"
    >
      <n-h1 prefix="bar">{{ article.title }}</n-h1>
      <n-p>
        {{ article.updateAt === article.createAt ? '发布' : '更新' }}于<n-time
          :time="article.updateAt * 1000"
          type="relative"
        />
        by {{ article.user.username }}
        <template v-if="userData.username === article.user.username">
          /
          <RouterNA :to="`/forum-edit/${article.id}`">编辑</RouterNA>
        </template>
      </n-p>
      <n-divider />

      <Markdown :source="article.content" />

      <section>
        <CommentList :site="`article-${articleId}`" :locked="article.locked" />
      </section>
    </ResultView>
  </MainLayout>
</template>
