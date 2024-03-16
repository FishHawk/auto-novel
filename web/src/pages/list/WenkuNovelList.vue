<script lang="ts" setup>
import { PlusOutlined } from '@vicons/material';
import { computed, watch } from 'vue';
import { useRoute } from 'vue-router';

import { WenkuNovelRepository } from '@/data/api';
import { runCatching } from '@/pages/result';
import { useWenkuSearchHistoryStore } from '@/data/stores/search_history';
import { useUserDataStore } from '@/data/stores/user_data';
import { Page } from '@/model/Page';
import { WenkuNovelOutlineDto } from '@/model/WenkuNovel';

import { Loader } from './components/NovelList.vue';

const userData = useUserDataStore();
const route = useRoute();
const searchHistory = useWenkuSearchHistoryStore();

const options = userData.isOldAss
  ? [
      {
        label: '分级',
        tags: ['一般向', 'R18'],
      },
    ]
  : [];

const loader: Loader<Page<WenkuNovelOutlineDto>> = (page, query, selected) => {
  if (userData.isOldAss) {
    return runCatching(
      WenkuNovelRepository.listNovel({
        page,
        pageSize: 24,
        query,
        level: selected[0] + 1,
      })
    );
  } else {
    return runCatching(
      WenkuNovelRepository.listNovel({
        page,
        pageSize: 24,
        query,
        level: 1,
      })
    );
  }
};

const search = computed(() => {
  return {
    suggestions: searchHistory.queries,
    tags: searchHistory.tags
      .sort((a, b) => Math.log2(b.used) - Math.log2(a.used))
      .map((it) => it.tag)
      .slice(0, 8),
  };
});

watch(
  route,
  async (route) => {
    let query = '';
    if (typeof route.query.query === 'string') {
      query = route.query.query;
    }
    searchHistory.addHistory(query);
  },
  { immediate: true }
);
</script>

<template>
  <div class="layout-content">
    <n-h1>文库小说</n-h1>

    <router-link to="/wenku-edit">
      <c-button
        label="新建小说"
        :icon="PlusOutlined"
        style="margin-bottom: 8px"
      />
    </router-link>

    <NovelList
      :search="search"
      :options="options"
      :loader="loader"
      v-slot="{ page }"
    >
      <NovelListWenku :items="page.items" />
    </NovelList>
  </div>
</template>

<style scoped>
.n-card-header__main {
  text-overflow: ellipsis;
}
</style>
