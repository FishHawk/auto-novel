<script lang="ts" setup>
import { SyncAltOutlined } from '@vicons/material';

type Filter = { query: string; selected: number[] };

const props = defineProps<{
  search: { suggestions: string[]; tags: string[] } | undefined;
  options: { label: string; tags: string[]; multiple?: boolean }[];
  filters: Filter;
}>();
const emits = defineEmits<{
  userInput: [];
}>();

const select = (
  optionIndex: number,
  index: number,
  multiple: boolean = false
) => {
  if (!multiple) {
    props.filters.selected[optionIndex] = index;
  } else {
    props.filters.selected[optionIndex] ^= 1 << index;
  }
  emits('userInput');
};

const selected = (
  optionIndex: number,
  index: number,
  multiple: boolean = false
) => {
  if (!multiple) {
    return index === props.filters.selected[optionIndex];
  } else {
    return (props.filters.selected[optionIndex] & (1 << index)) != 0;
  }
};

const invertSelection = (optionIndex: number) => {
  const option = props.options[optionIndex];
  if (option.multiple === true) {
    props.filters.selected[optionIndex] ^= 2 ** option.tags.length - 1;
    emits('userInput');
  }
};
</script>

<template>
  <n-flex
    v-if="search !== undefined || options.length >= 0"
    size="large"
    vertical
    style="width: 100%; margin-top: 8px"
  >
    <c-action-wrapper v-if="search !== undefined" title="搜索" size="large">
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
    </c-action-wrapper>
    <c-action-wrapper
      v-for="(option, optionIndex) in options"
      :title="option.label"
      align="baseline"
      size="large"
    >
      <n-flex :size="[16, 4]">
        <n-text
          v-for="(tag, index) in option.tags"
          text
          :type="
            selected(optionIndex, index, option.multiple)
              ? 'primary'
              : 'default'
          "
          @click="select(optionIndex, index, option.multiple)"
          style="cursor: pointer"
        >
          {{ tag }}
        </n-text>
        <n-button
          v-if="option.multiple"
          type="primary"
          text
          @click="invertSelection(optionIndex)"
        >
          <n-icon :component="SyncAltOutlined" />
        </n-button>
      </n-flex>
    </c-action-wrapper>
  </n-flex>
</template>
