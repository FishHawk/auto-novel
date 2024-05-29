<script lang="ts" setup>
import { SearchOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { Setting } from '@/model/Setting';

import {
  BookshelfLocalUtil,
  useBookshelfLocalStore,
} from '../BookshelfLocalStore';

const props = defineProps<{
  selectable?: boolean;
}>();

const { setting } = Locator.settingRepository();

const store = useBookshelfLocalStore();
const { volumes } = storeToRefs(store);

store.loadVolumes();

const enableRegexMode = ref(false);
const filenameSearch = ref('');

const sortedVolumes = computed(() => {
  return BookshelfLocalUtil.filterAndSortVolumes(volumes.value, {
    query: filenameSearch.value,
    enableRegexMode: enableRegexMode.value,
    order: setting.value.localVolumeOrder,
  });
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
  if (!selected) {
    selectedIds.value = selectedIds.value.filter((it) => it != id);
  } else if (!selectedIds.value.includes(id)) {
    selectedIds.value.push(id);
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
      <n-input
        clearable
        size="small"
        v-model:value="filenameSearch"
        type="text"
        placeholder="搜索文件名"
        style="max-width: 400px"
      >
        <template #suffix> <n-icon :component="SearchOutlined" /> </template>
      </n-input>

      <tag-button label="正则" v-model:checked="enableRegexMode" />
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
        <n-checkbox
          v-if="selectable"
          :checked="selectedIds.includes(volume.id)"
          @update:checked="
            (selected: boolean) => toggleSelect(volume.id, selected)
          "
          style="margin-right: 8px"
        />
        <bookshelf-local-list-item :volume="volume" />
      </n-list-item>
    </n-list>
  </n-scrollbar>
</template>
