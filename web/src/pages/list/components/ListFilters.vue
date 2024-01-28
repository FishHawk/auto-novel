<script lang="ts" setup>
type Filter = { query: string; selected: number[] };

const props = defineProps<{
  search: { suggestions: string[]; tags: string[] } | undefined;
  options: { label: string; tags: string[] }[];
  filters: Filter;
}>();
const emits = defineEmits<{
  userInput: [];
}>();

function select(optionIndex: number, index: number) {
  props.filters.selected[optionIndex] = index;
  emits('userInput');
}
</script>

<template>
  <n-flex
    v-if="search !== undefined || options.length >= 0"
    size="large"
    vertical
    style="width: 100%; margin-top: 8px"
  >
    <n-flex
      v-if="search !== undefined"
      size="large"
      align="baseline"
      :wrap="false"
    >
      <n-text style="white-space: nowrap" depth="3">搜索</n-text>
      <input-with-suggestion
        v-model:value="filters.query"
        :suggestions="search.suggestions"
        :tags="search.tags"
        :placeholder="`中/日文标题或作者`"
        style="flex: 0 1 400px; margin-right: 8px"
        :input-props="{ spellcheck: false }"
        @keyup.enter="emits('userInput')"
        @select="
          (query: string) => {
            filters.query = query;
            emits('userInput');
          }
        "
      />
    </n-flex>
    <n-flex
      v-for="(option, optionIndex) in options"
      size="large"
      align="baseline"
      :wrap="false"
    >
      <n-text style="white-space: nowrap" depth="3">{{ option.label }}</n-text>
      <n-flex size="large">
        <n-button
          v-for="(tag, index) in option.tags"
          text
          :type="
            index === filters.selected[optionIndex] ? 'primary' : 'default'
          "
          @click="select(optionIndex, index)"
        >
          {{ tag }}
        </n-button>
      </n-flex>
    </n-flex>
  </n-flex>
</template>
