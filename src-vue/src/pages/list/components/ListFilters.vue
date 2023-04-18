<script lang="ts" setup>
import { ref } from 'vue';
import { SearchFilled } from '@vicons/material';

const props = defineProps<{
  search: boolean;
  options: { label: string; tags: string[] }[];
  filters: { query: string; selected: number[] };
}>();
const query = ref(props.filters.query);
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
          v-model:value="query"
          :placeholder="`中/日文标题或作者`"
          style="max-width: 400px"
          @keyup.enter="filters.query = query"
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
            @click="filters.selected[optionIndex] = index"
          >
            {{ tag }}
          </n-button>
        </n-space>
      </td>
    </tr>
  </table>
</template>
