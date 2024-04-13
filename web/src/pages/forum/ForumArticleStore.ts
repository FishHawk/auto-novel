import { createGlobalState } from '@vueuse/core';

import { Locator } from '@/data';
import { Article } from '@/model/Article';
import { Result, runCatching } from '@/util/result';

export const useForumArticleStore = createGlobalState(() => {
  // state
  const articleResult = ref<Result<Article>>();
  const id = ref<string>();

  // actions
  const load = async (articleId: string, force = false) => {
    if (!force && id.value === articleId && articleResult.value?.ok) {
      return articleResult.value;
    }

    articleResult.value = undefined;
    id.value = articleId;
    const result = await runCatching(
      Locator.articleRepository.getArticle(articleId)
    );
    if (articleId !== id.value) return;
    articleResult.value = result;
    return result;
  };

  return { articleResult, load };
});
