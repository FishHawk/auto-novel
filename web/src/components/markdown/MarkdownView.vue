<script lang="ts" setup>
import { XMarkdownContent } from '@novelia/web-kit';

import { markdownByMode, type MarkdownMode } from './markdown';

defineProps<{
  mode: MarkdownMode;
  source: string;
}>();
</script>

<template>
  <XMarkdownContent :source="source" :markdown="markdownByMode[mode]" />
</template>
