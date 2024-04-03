import { lazyUseLocalStorage } from '@/util/storage';

interface SearchHistory {
  queries: string[];
  tags: { tag: string; used: number }[];
}

const factory = (key: string) => {
  const lazyStorage = lazyUseLocalStorage<SearchHistory>(key, {
    queries: [],
    tags: [],
  });

  const addHistory = lazyStorage.with((history, query: string) => {
    query = query.trim();
    const parts = query.split(' ');

    if (query === '' || parts.length === 0) {
      return;
    }

    const tags = parts.filter((it) => it.endsWith('$'));
    tags.forEach((part) => {
      const inHistory = history.value.tags.find((it) => it.tag === part);
      if (inHistory === undefined) {
        history.value.tags.push({ tag: part, used: 1 });
      } else {
        inHistory.used += 1;
      }
    });

    const newQueries = history.value.queries.filter((it) => it !== query);
    newQueries.unshift(query);
    history.value.queries = newQueries.slice(0, 8);
  });

  const clear = lazyStorage.with((history) => {
    history.value.queries = [];
    history.value.tags = [];
  });

  return {
    ref: lazyStorage.ref,
    addHistory,
    clear,
  };
};

export const WebSearchHistoryRepository = factory('webSearchHistory');

export const WenkuSearchHistoryRepository = factory('wenkuSearchHistory');
