<script lang="ts" setup>
import { SearchOutlined } from '@vicons/material';

const value = defineModel<{
  query: string;
  enableRegexMode: boolean;
}>('value', {
  required: true,
});

const attrs = useAttrs()
</script>
<template>
  <n-input clearable size="small" v-model:value="value.query" type="text" v-bind="attrs">
    <template #suffix> <n-icon :component="SearchOutlined" /> </template>
  </n-input>

  <tag-button label="正则" v-model:checked="value.enableRegexMode" />
</template>
