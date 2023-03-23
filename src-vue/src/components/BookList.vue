<script lang="ts" setup>
import { computed } from 'vue';

import { BookListPageDto, BookRankPageDto } from '../data/api/api_novel';
import { ResultState } from '../data/api/result';
import { buildMetadataUrl } from '../data/provider';
import { errorToString } from '../data/handle_error';

const props = defineProps<{
  page: number;
  pageNumber: number;
  bookPage: ResultState<BookListPageDto | BookRankPageDto>;
}>();

const emit = defineEmits<{
  (e: 'update:page', id: number): void;
}>();

const pageInternal = computed({
  get(): number {
    return props.page;
  },
  set(newValue: number): void {
    emit('update:page', newValue);
  },
});
</script>

<template>
  <div>
    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="pageInternal"
      :page-count="pageNumber"
      :page-slot="7"
      style="margin-top: 20px"
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
        <template v-if="'extra' in item">
          <div v-for="extraLine in item.extra.split('\n')" style="color: #666">
            {{ extraLine }}
          </div>
        </template>
        <template v-else>
          <div style="color: #666">
            日文({{ item.countJp }}/{{ item.total }}) 中文({{ item.countZh }}/{{
              item.total
            }})
          </div>
        </template>
        <n-divider />
      </div>

      <n-empty
        v-if="bookPage.value.items.length === 0"
        description="空列表"
      />
    </div>
    <n-result
      v-if="bookPage && !bookPage.ok"
      status="error"
      title="加载错误"
      :description="errorToString(bookPage.error)"
    />
    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="pageInternal"
      :page-count="pageNumber"
      :page-slot="7"
    />
  </div>
</template>
