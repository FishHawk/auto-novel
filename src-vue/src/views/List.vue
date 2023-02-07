<script lang="ts" setup>
import { ref, watch } from 'vue';

import { ResultState } from '../api/result';
import ApiNovel, { BookPageDto, stateToFileList } from '../api/api_novel';
import { buildMetadataUrl } from '../data/provider';

const providerOption = ref('');
const providerOptions = [
  { label: '全部', value: '' },
  { label: 'Kakuyomu', value: 'kakuyomu' },
  { label: '成为小说家吧', value: 'syosetu' },
  { label: 'Novelup', value: 'novelup' },
  { label: 'Hameln', value: 'hameln' },
  { label: 'Pixiv', value: 'pixiv' },
];
const sortOption = ref<'created' | 'changed'>('changed');
const sortOptions = [
  { label: '更新时间', value: 'changed' },
  { label: '创建时间', value: 'created' },
];

const currentPage = ref(1);
const total = ref(1);
const bookPage = ref<ResultState<BookPageDto>>();

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
      <table>
        <tr>
          <td nowrap="nowrap" style="vertical-align: top; padding-right: 10px">
            来源
          </td>
          <td>
            <n-radio-group
              v-model:value="providerOption"
              name="providerOptions"
            >
              <n-space>
                <n-radio
                  v-for="option in providerOptions"
                  :key="option.label"
                  :value="option.value"
                  >{{ option.label }}</n-radio
                >
              </n-space>
            </n-radio-group>
          </td>
        </tr>

        <tr>
          <td nowrap="nowrap" style="vertical-align: top; padding-right: 10px">
            排序
          </td>
          <td>
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
          </td>
        </tr>
      </table>
    </section>

    <n-pagination
      v-model:page="currentPage"
      :page-count="Math.floor(total / 10)"
      :page-slot="7"
    />
    <n-divider />
    <div v-if="bookPage?.ok">
      <div v-for="item in bookPage.value.items">
        <n-h3 class="title" style="margin-bottom: 4px">
          <n-a
            :href="`/novel/${item.providerId}/${item.bookId}`"
            target="_blank"
          >
            {{ item.titleJp }}
          </n-a>
        </n-h3>
        <div>{{ item.titleZh }}</div>
        <n-a
          :href="buildMetadataUrl(item.providerId, item.bookId)"
          target="_blank"
        >
          {{ buildMetadataUrl(item.providerId, item.bookId) }}
        </n-a>

        <div
          v-for="group in stateToFileList(
            item.providerId,
            item.bookId,
            item.state
          )"
        >
          <n-space>
            <span>{{ group.label }}</span>
            <n-a v-for="file in group.files" :href="file.url" target="_blank">
              {{ file.label }}
            </n-a>
          </n-space>
        </div>
        <n-divider />
      </div>
    </div>
    <n-pagination
      v-model:page="currentPage"
      :page-count="Math.floor(total / 10)"
      :page-slot="7"
    />
  </div>
</template>
