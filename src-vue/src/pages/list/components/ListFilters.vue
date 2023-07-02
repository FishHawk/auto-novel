<script lang="ts" setup>
import { SearchFilled } from '@vicons/material';

type Filter = { query: string; selected: number[] };

const props = defineProps<{
  search: boolean;
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
  <table v-if="search || options.length >= 0" style="border-spacing: 0px 8px">
    <tr v-if="search">
      <td
        nowrap="nowrap"
        style="vertical-align: center; padding-right: 20px; color: grey"
      >
        搜索
      </td>
      <td>
        <n-input
          v-model:value="filters.query"
          :placeholder="`中/日文标题或作者`"
          style="max-width: 400px"
          @keyup.enter="emits('userInput')"
        >
          <template #suffix>
            <n-icon :component="SearchFilled" />
          </template>
        </n-input>
      </td>
    </tr>
    <tr v-for="(option, optionIndex) in options">
      <td
        nowrap="nowrap"
        style="vertical-align: top; padding-right: 20px; color: grey"
      >
        {{ option.label }}
      </td>
      <td>
        <n-space :size="[16, 4]">
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
        </n-space>
      </td>
    </tr>
  </table>
</template>