<script lang="ts" setup>
import { XMarkdownContent } from '@novelia/web-kit';

import { markdown } from './markdown';

defineProps<{
  source: string;
}>();
</script>

<template>
  <XMarkdownContent :source="source" :markdown="markdown" />
</template>
