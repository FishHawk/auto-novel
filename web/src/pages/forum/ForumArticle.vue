<script lang="ts" setup>
import { Locator } from '@/data';

import { useForumArticleStore } from './ForumArticleStore';

const props = defineProps<{ articleId: string }>();

const { userData, asAdmin } = Locator.userDataRepository();

const { articleResult, load } = useForumArticleStore();

watch(
  props,
  ({ articleId }) =>
    load(articleId).then((result) => {
      if (result?.ok) {
        document.title = result.value.title;
      }
    }),
  { immediate: true }
);
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

      <comment-list :site="`article-${articleId}`" :locked="article.locked" />
    </c-result>
  </div>
</template>
