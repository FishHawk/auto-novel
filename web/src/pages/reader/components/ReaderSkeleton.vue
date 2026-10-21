<template>
  <div role="status" class="py-5">
    <span class="sr-only">正在加载章节正文…</span>
    <div aria-hidden="true">
      <div class="h-6 w-3/4 skeleton bg-border" />
      <div class="mt-3 h-5 w-1/2 skeleton" />
      <div class="my-6 border-t border-divider" />
      <div v-for="paragraph in 3" :key="paragraph" class="mb-8 space-y-4">
        <div class="ml-8 h-4 skeleton" />
        <div class="h-4 skeleton" />
        <div class="h-4 w-5/6 skeleton" />
      </div>
    </div>
  </div>
</template>
