<script lang="ts" setup>
import { PlusOutlined } from '@vicons/material';

import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { Page } from '@/data/api/common';
import { useUserDataStore } from '@/data/stores/user_data';

import { Loader } from './components/NovelList.vue';

const userData = useUserDataStore();

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
    return ApiWenkuNovel.listNovel({
      page,
      pageSize: 24,
      query,
      level: selected[0] + 1,
    });
  } else {
    return ApiWenkuNovel.listNovel({
      page,
      pageSize: 24,
      query,
      level: 1,
    });
  }
};
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
      :search="{
        suggestions: [],
        tags: [],
      }"
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
