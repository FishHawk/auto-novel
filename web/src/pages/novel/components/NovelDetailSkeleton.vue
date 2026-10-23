<script setup lang="ts">
import CLayout from '@/components/CLayout.vue';

defineProps<{
  wenku?: boolean;
  wide?: boolean;
}>();
</script>

<template>
  <div role="status">
    <span class="sr-only">正在加载小说详情…</span>
    <CLayout
      aria-hidden="true"
      :sidebar="!!wide && !wenku"
      :sidebar-width="320"
    >
      <div class="min-w-0" :class="{ 'pt-[18px]': !wenku }">
        <div v-if="wenku" class="flex gap-3 py-5">
          <div class="h-56 w-40 shrink-0 skeleton" />
          <div class="min-w-0 flex-1 space-y-4 pt-2">
            <div class="h-7 w-5/6 skeleton bg-border" />
            <div v-for="index in 3" :key="index" class="h-6 w-3/4 skeleton" />
          </div>
        </div>
        <template v-else>
          <div class="h-7 w-4/5 skeleton bg-border" />
          <div class="mt-3 h-6 w-2/3 skeleton" />
          <div class="mt-4 h-5 w-2/5 skeleton" />
        </template>
        <div class="mt-4 flex gap-3">
          <div v-for="index in 3" :key="index" class="h-8 w-20 skeleton" />
        </div>
        <div class="my-6 space-y-3">
          <div v-for="index in 4" :key="index" class="h-4 skeleton" />
          <div class="h-4 w-2/3 skeleton" />
        </div>
        <div class="flex gap-2">
          <div v-for="index in 4" :key="index" class="h-5 w-14 skeleton" />
        </div>
        <div v-if="wenku" class="mt-4 flex gap-1 overflow-hidden">
          <div
            v-for="index in 5"
            :key="index"
            class="h-36 w-26 shrink-0 skeleton"
          />
        </div>
        <div class="mt-6 border-t border-divider pt-5">
          <div class="mb-5 h-6 w-32 skeleton bg-border" />
          <div
            v-for="index in 4"
            :key="index"
            class="mb-4 h-5 w-4/5 skeleton"
          />
        </div>
      </div>
      <template #sidebar>
        <div aria-hidden="true" class="pr-4">
          <div class="my-[18px] h-7 w-20 skeleton bg-border" />
          <div class="space-y-5">
            <div v-for="index in 10" :key="index" class="h-5 skeleton" />
          </div>
        </div>
      </template>
    </CLayout>
  </div>
</template>
