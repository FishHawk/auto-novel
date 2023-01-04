<script lang="ts" setup>
import { ref, watch } from 'vue';

import { ResultRef } from '../api/result';
import ApiNovel, { BookPageDto, stateToFileList } from '../api/api_novel';
import { buildMetadataUrl } from '../data/provider';

const providerOption = ref('');
const providerOptions = [
  { label: '全部', value: '' },
  { label: 'Kakuyomu', value: 'kakuyomu' },
  { label: 'Syosetu', value: 'syosetu' },
  { label: 'Pixiv', value: 'pixiv' },
  { label: 'Hameln', value: 'hameln' },
  { label: 'Novelup', value: 'novelup' },
];
const sortOption = ref<'created' | 'changed'>('created');
const sortOptions = [
  { label: '创建时间', value: 'created' },
  { label: '更新时间', value: 'changed' },
];

const currentPage = ref(1);
const total = ref(1);
const bookPage: ResultRef<BookPageDto> = ref();

async function loadPage(page: number) {
  bookPage.value = undefined;
  const result = await ApiNovel.list(
    currentPage.value - 1,
    providerOption.value,
    sortOption.value
  );
  if (currentPage.value == page) {
    bookPage.value = result;
    if (result.ok) {
      total.value = result.value.total;
    }
  }
}

watch(currentPage, (page) => loadPage(page), { immediate: true });
watch(providerOption, (_) => {
  currentPage.value = 1;
  loadPage(currentPage.value);
});
watch(sortOption, (_) => {
  currentPage.value = 1;
  loadPage(currentPage.value);
});
</script>

<template>
  <div class="content">
    <section style="margin: 24px 0">
      <div style="margin-bottom: 10px">
        <n-text style="margin-right: 14px; opacity: 0.4">来源</n-text>
        <n-radio-group v-model:value="providerOption" name="providerOptions">
          <n-space>
            <n-radio
              v-for="option in providerOptions"
              :key="option.label"
              :value="option.value"
              >{{ option.label }}</n-radio
            >
          </n-space>
        </n-radio-group>
      </div>

      <div style="margin-bottom: 10px">
        <n-text style="margin-right: 14px; opacity: 0.4">排序</n-text>
        <n-radio-group v-model:value="sortOption" name="sortOptions">
          <n-space>
            <n-radio
              v-for="option in sortOptions"
              :key="option.label"
              :value="option.value"
              >{{ option.label }}</n-radio
            >
          </n-space>
        </n-radio-group>
      </div>
    </section>

    <n-pagination
      v-model:page="currentPage"
      :page-count="Math.floor(total / 10)"
      show-quick-jumper
    />
    <n-divider />
    <div v-if="bookPage?.ok">
      <div v-for="item in bookPage.value.items">
        <n-h2 class="title">
          <n-a
            :href="`/novel/${item.providerId}/${item.bookId}`"
            target="_blank"
          >
            {{ item.titleJp }}
          </n-a>
        </n-h2>
        <div>{{ item.titleZh }}</div>
        <n-a
          :href="buildMetadataUrl(item.providerId, item.bookId)"
          target="_blank"
        >
          {{ buildMetadataUrl(item.providerId, item.bookId) }}
        </n-a>

        <n-space
          v-for="group in stateToFileList(
            item.providerId,
            item.bookId,
            item.state
          )"
        >
          <span>{{ group.label }}</span>
          <n-space spacer="|">
            <n-a v-for="file in group.files" :href="file.url" target="_blank">
              {{ file.label }}
            </n-a>
          </n-space>
        </n-space>
        <n-divider />
      </div>
    </div>
    <n-pagination
      v-model:page="currentPage"
      :page-count="Math.floor(total / 10)"
      show-quick-jumper
    />
  </div>
</template>
