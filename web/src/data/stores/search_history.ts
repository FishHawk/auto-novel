import { defineStore } from 'pinia';

export interface WebSearchHistory {
  queries: string[];
  tags: { tag: string; used: number }[];
}

export const useWebSearchHistoryStore = defineStore('webSearchHistory', {
  state: () =>
    <WebSearchHistory>{
      queries: [],
      tags: [],
    },
  actions: {
    addHistory(query: string) {
      query = query.trim();
      const parts = query.split(' ');

      if (query === '' || parts.length === 0) {
        return;
      }

      const tags = parts.filter((it) => it.endsWith('$'));
      tags.forEach((part) => {
        const inHistory = this.tags.find((it) => it.tag === part);
        if (inHistory === undefined) {
          this.tags.push({ tag: part, used: 1 });
        } else {
          inHistory.used += 1;
        }
      });

      // 只有在不是单个tag搜索时注册查询历史
      if (parts.length > 1 || tags.length === 0) {
        const newQueries = this.queries.filter((it) => it !== query);
        newQueries.unshift(query);
        this.queries = newQueries.slice(0, 8);
      }
    },
  },
  persist: true,
});
