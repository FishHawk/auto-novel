<script lang="ts" setup>
import { Locator } from '@/data';
import { Setting } from '@/model/Setting';

import {
  BookshelfLocalUtil,
  useBookshelfLocalStore,
} from '../BookshelfLocalStore';

const props = defineProps<{
  selectable?: boolean;
  favoredId: string;
}>();

const { setting } = Locator.settingRepository();

const store = useBookshelfLocalStore();
const { volumes } = storeToRefs(store);

store.loadVolumes();

const search = reactive({
  query: '',
  enableRegexMode: false,
});

const sortedVolumes = computed(() => {
  return BookshelfLocalUtil.filterAndSortVolumes(
    volumes.value.filter((v) => v.favoriteId == props.favoredId),
    {
      ...search,
      order: setting.value.localVolumeOrder,
    },
  );
});

const selectedIds = ref<string[]>([]);

watch(
  () => props.selectable,
  (selectable) => {
    if (selectable !== false) {
      selectedIds.value = [];
    }
  },
);

const toggleSelect = (id: string, selected: boolean) => {
  if (selected) {
    selectedIds.value.push(id);
  } else {
    selectedIds.value = selectedIds.value.filter((it) => it != id);
  }
};

defineExpose({
  selectedIds,
  selectAll: () => {
    selectedIds.value = sortedVolumes.value.map((it) => it.id);
  },
  invertSelection: () => {
    selectedIds.value = sortedVolumes.value
      .map((it) => it.id)
      .filter((it) => !selectedIds.value.includes(it));
  },
});
</script>

<template>
  <n-flex vertical>
    <c-action-wrapper title="搜索">
      <search-input
        v-model:value="search"
        placeholder="搜索文件名"
        style="max-width: 400px"
      />
    </c-action-wrapper>

    <c-action-wrapper title="排序" align="center">
      <order-sort
        v-model:value="setting.localVolumeOrder"
        :options="Setting.localVolumeOrderOptions"
      />
    </c-action-wrapper>
  </n-flex>

  <n-divider style="margin: 16px 0 8px" />

  <n-spin v-if="sortedVolumes === undefined" style="margin-top: 20px" />

  <n-empty
    v-else-if="sortedVolumes.length === 0"
    description="没有文件"
    style="margin-top: 20px"
  />

  <n-scrollbar v-else trigger="none" :size="24" style="flex: auto">
    <n-list style="padding-bottom: 48px; padding-right: 12px">
      <n-list-item v-for="volume of sortedVolumes ?? []" :key="volume.id">
        <bookshelf-local-list-item :volume="volume" />
        <c-select-overlay
          v-if="selectable"
          :checked="selectedIds.includes(volume.id)"
          @update:checked="
            (checked: boolean) => toggleSelect(volume.id, checked)
          "
        />
      </n-list-item>
    </n-list>
  </n-scrollbar>
</template>
