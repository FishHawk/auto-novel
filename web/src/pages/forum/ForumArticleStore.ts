import { Locator } from '@/data';
import { Article } from '@/model/Article';
import { Result, runCatching } from '@/util/result';

const repo = Locator.articleRepository;

type ArticleStore = {
  articleResult: Result<Article> | undefined;
};

export const useArticleStore = (articleId: string) => {
  return defineStore(`Article/${articleId}`, {
    state: () =>
      <ArticleStore>{
        articleResult: undefined,
      },
    actions: {
      async loadArticle(force = false) {
        if (!force && this.articleResult?.ok) {
          return this.articleResult;
        }

        this.articleResult = undefined;
        const result = await runCatching(repo.getArticle(articleId));
        this.articleResult = result;

        return this.articleResult;
      },

      async updateArticle(json: Parameters<typeof repo.updateArticle>[1]) {
        await repo.updateArticle(articleId, json);
        this.loadArticle(true);
      },
    },
  })();
};
