import { defineStore } from 'pinia';

interface SearchHistory {
  queries: string[];
  tags: { tag: string; used: number }[];
}

const useWebSearchHistoryStoreFactory = (id: string) =>
  defineStore(id, {
    state: () =>
      <SearchHistory>{
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

        const newQueries = this.queries.filter((it) => it !== query);
        newQueries.unshift(query);
        this.queries = newQueries.slice(0, 8);
      },
      clear() {
        this.queries = [];
        this.tags = [];
      },
    },
    persist: true,
  });

export const useWebSearchHistoryStore =
  useWebSearchHistoryStoreFactory('webSearchHistory');

export const useWenkuSearchHistoryStore =
  useWebSearchHistoryStoreFactory('wenkuSearchHistory');
