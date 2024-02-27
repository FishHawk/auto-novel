<script lang="ts" setup>
import { ref } from 'vue';

import { WenkuNovelOutlineDto } from '@/data/api/api_wenku_novel';

const props = defineProps<{
  items: WenkuNovelOutlineDto[];
}>();

const enableSelectMode = ref(false);
const selectedNovels = ref<string[]>([]);

const toggleSelectMode = (enable: boolean) => {
  enableSelectMode.value = enable;
  if (enable === false) {
    selectedNovels.value = [];
  }
};
const toggleNovelSelect = (novel: string, selected: boolean) => {
  if (!selected) {
    selectedNovels.value = selectedNovels.value.filter((it) => it != novel);
  } else if (!selectedNovels.value.includes(novel)) {
    selectedNovels.value.push(novel);
  }
};

const getSelectedNovels = () => {
  return props.items.filter((it) => selectedNovels.value.includes(it.id));
};

const selectAll = () => {
  selectedNovels.value = props.items.map((it) => it.id);
};

const invertSelection = () => {
  selectedNovels.value = props.items
    .map((it) => it.id)
    .filter((it) => !selectedNovels.value.includes(it));
};

defineExpose({
  toggleSelectMode,
  getSelectedNovels,
  selectAll,
  invertSelection,
});
</script>

<template>
  <n-grid :x-gap="12" :y-gap="12" cols="2 600:4">
    <n-grid-item v-for="item in items">
      <router-link :to="`/wenku/${item.id}`">
        <ImageCard
          :src="item.cover"
          :title="item.titleZh ? item.titleZh : item.title"
        />
        <n-checkbox
          v-if="enableSelectMode"
          :checked="selectedNovels.includes(item.id)"
          @update:checked="(selected: boolean) => toggleNovelSelect(item.id, selected)"
          style="margin-right: 8px"
        />
      </router-link>
    </n-grid-item>
  </n-grid>
</template>
