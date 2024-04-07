<script lang="ts" setup>
import { Locator } from '@/data';
import { Article } from '@/model/Article';
import { Result, runCatching } from '@/util/result';

const { articleId } = defineProps<{ articleId: string }>();

const { userData, asAdmin } = Locator.userDataRepository();

const articleResult = ref<Result<Article>>();

onMounted(async () => {
  const result = await runCatching(
    Locator.articleRepository.getArticle(articleId)
  );
  articleResult.value = result;
  if (result.ok) {
    document.title = result.value.title;
  }
});
</script>

<template>
  <div class="layout-content">
    <c-result :result="articleResult" v-slot="{ value: article }">
      <n-h1 prefix="bar">{{ article.title }}</n-h1>
      <n-text v-if="article.hidden" depth="3">[隐藏]</n-text>
      <n-p>
        {{ article.updateAt === article.createAt ? '发布' : '更新' }}于<n-time
          :time="article.updateAt * 1000"
          type="relative"
        />
        by {{ article.user.username }}
        <template
          v-if="userData.info?.username === article.user.username || asAdmin"
        >
          /
          <c-a :to="`/forum-edit/${article.id}?category=${article.category}`">
            编辑
          </c-a>
        </template>
      </n-p>
      <n-divider />

      <Markdown :source="article.content" />

      <section>
        <CommentList :site="`article-${articleId}`" :locked="article.locked" />
      </section>
    </c-result>
  </div>
</template>
