<script setup lang="ts">
import { InfoOutlined } from '@vicons/material';

import { forumUrl } from '@/config';
</script>

<template>
  <div
    class="flex items-center gap-2 rounded-md border border-primary/15 bg-primary-soft px-3 py-2 text-sm text-ink"
  >
    <InfoOutlined class="size-4 shrink-0 text-primary" aria-hidden="true" />
    <p class="flex flex-wrap">
      <span>发言请遵守</span>
      <span class="inline-flex">
        <a
          :href="`${forumUrl}/rules`"
          target="_blank"
          rel="noopener noreferrer"
          class="font-semibold text-primary underline underline-offset-2 hover:text-primary-hover focus-visible:rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          社区守则
        </a>
      </span>
    </p>
  </div>
</template>
