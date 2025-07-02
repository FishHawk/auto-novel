import { useLocalStorage } from '@/util';

import { LSKey } from '../LocalStorage';

interface SearchHistory {
  queries: string[];
  tags: { tag: string; used: number }[];
}

const createSearchHistoryRepository = (key: string) => {
  const ref = useLocalStorage<SearchHistory>(key, {
    queries: [],
    tags: [],
  });

  const addHistory = (query: string) => {
    query = query.trim();
    const parts = query.split(' ');

    if (query === '' || parts.length === 0) {
      return;
    }

    const tags = parts.filter((it) => it.endsWith('$'));
    tags.forEach((part) => {
      const inHistory = ref.value.tags.find((it) => it.tag === part);
      if (inHistory === undefined) {
        ref.value.tags.push({ tag: part, used: 1 });
      } else {
        inHistory.used += 1;
      }
    });

    const newQueries = ref.value.queries.filter((it) => it !== query);
    newQueries.unshift(query);
    ref.value.queries = newQueries.slice(0, 8);
  };

  const clear = () => {
    ref.value.queries = [];
    ref.value.tags = [];
  };

  return {
    ref,
    addHistory,
    clear,
  };
};

export const createWebSearchHistoryRepository = () =>
  createSearchHistoryRepository(LSKey.SearchHistoryWeb);

export const createWenkuSearchHistoryRepository = () =>
  createSearchHistoryRepository(LSKey.SearchHistoryWenku);
