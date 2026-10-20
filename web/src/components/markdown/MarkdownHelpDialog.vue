<script setup lang="ts">
import { XButton } from '@novelia/web-kit';
import { HelpOutlineOutlined } from '@vicons/material';

import MarkdownGuideModal from './MarkdownGuideModal.vue';

const open = ref(false);
</script>

<template>
  <XButton
    variant="ghost"
    size="xs"
    class="hover:bg-divider/40 hover:text-ink"
    @click="open = true"
  >
    <HelpOutlineOutlined class="size-4" aria-hidden="true" />
    Markdown 语法帮助
  </XButton>
  <MarkdownGuideModal v-model:open="open" />
</template>
