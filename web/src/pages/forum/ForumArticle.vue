<script lang="ts" setup>
import { ArticleRepository } from '@/data/api';
import { Result, runCatching } from '@/util/result';
import { useUserDataStore } from '@/data/stores/user_data';
import { Article } from '@/model/Article';

const route = useRoute();
const userData = useUserDataStore();

const articleId = route.params.id as string;
const articleResult = ref<Result<Article>>();

onMounted(async () => {
  const result = await runCatching(ArticleRepository.getArticle(articleId));
  articleResult.value = result;
  if (result.ok) {
    document.title = result.value.title;
  }
});
</script>

<template>
  <div class="layout-content">
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
        <template
          v-if="userData.username === article.user.username || userData.asAdmin"
        >
          /
          <RouterNA
            :to="`/forum-edit/${article.id}?category=${article.category}`"
            >编辑</RouterNA
          >
        </template>
      </n-p>
      <n-divider />

      <Markdown :source="article.content" />

      <section>
        <CommentList :site="`article-${articleId}`" :locked="article.locked" />
      </section>
    </ResultView>
  </div>
</template>
