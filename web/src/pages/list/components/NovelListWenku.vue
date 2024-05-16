<script lang="ts" setup>
import { WenkuNovelOutlineDto } from '@/model/WenkuNovel';

const props = defineProps<{
  items: WenkuNovelOutlineDto[];
  selectable?: boolean;
}>();

const selectedNovels = ref<string[]>([]);

watch(
  () => props.selectable,
  (selectable) => {
    if (selectable !== false) {
      selectedNovels.value = [];
    }
  },
);
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
  getSelectedNovels,
  selectAll,
  invertSelection,
});
</script>

<template>
  <n-grid :x-gap="12" :y-gap="12" cols="2 500:3 800:4">
    <n-grid-item v-for="item in items">
      <router-link :to="`/wenku/${item.id}`">
        <ImageCard
          :src="item.cover"
          :title="item.titleZh ? item.titleZh : item.title"
        />
      </router-link>
      <n-checkbox
        v-if="selectable"
        :checked="selectedNovels.includes(item.id)"
        @update:checked="
          (selected: boolean) => toggleNovelSelect(item.id, selected)
        "
        style="margin-right: 8px"
      />
    </n-grid-item>
  </n-grid>
</template>
