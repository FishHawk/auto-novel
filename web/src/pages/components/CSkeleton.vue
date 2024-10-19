<script lang="ts" setup>
defineProps<{
  type: 'webNovel' | 'wenkuNovel' | 'webNovelLite' | 'webNovelHistory';
}>();
import { checkIsMobile } from '@/pages/util';
const isMobile = checkIsMobile();
</script>

<template>
  <template v-if="type === 'webNovel'">
    <n-space size="small" vertical v-for="item in 20">
      <template v-if="!isMobile">
        <n-skeleton text :repeat="2" style="width: 60%" />
        <n-skeleton text style="width: 25%" />
        <n-skeleton text style="width: 60%" />
        <n-skeleton text style="width: 40%" />
        <n-skeleton text style="width: 15%" />
      </template>
      <template v-else>
        <n-skeleton text :repeat="2" />
        <n-skeleton text style="width: 80%" />
        <n-skeleton text :repeat="2" />
        <n-skeleton text style="width: 80%" />
        <n-skeleton text style="width: 40%" />
      </template>
      <n-divider />
    </n-space>
  </template>

  <template v-if="type === 'webNovelLite' || type === 'webNovelHistory'">
    <n-space size="small" vertical v-for="item in 20">
      <template v-if="!isMobile">
        <n-skeleton text :repeat="2" style="width: 60%" />
        <n-skeleton text style="width: 40%" />
        <n-skeleton text style="width: 15%" />
        <n-skeleton :width="30" round v-if="type === 'webNovelHistory'" />
      </template>
      <template v-else>
        <n-skeleton text :repeat="2" />
        <n-skeleton text style="width: 80%" />
        <n-skeleton text style="width: 40%" />
        <n-skeleton :width="30" round v-if="type === 'webNovelHistory'" />
      </template>
      <n-divider />
    </n-space>
  </template>

  <template v-if="type === 'wenkuNovel'">
    <n-grid :x-gap="12" :y-gap="12" cols="2 500:3 800:4">
      <n-grid-item v-for="item in 20">
        <div class="skeleton-cover">
          <n-skeleton class="skeleton-img" />
          <img class="skeleton-bg" src="@/image/girl.webp" alt="girl" />
        </div>
        <n-skeleton class="skeleton-title" text style="width: 80%" />
      </n-grid-item>
    </n-grid>
  </template>
</template>

<style scoped>
.skeleton-cover {
  position: relative;
  overflow: hidden;
}
.skeleton-img {
  width: 100%;
  display: block;
  aspect-ratio: 1 / 1.5;
  height: auto;
  border-radius: 2px;
}
.skeleton-bg {
  position: absolute;
  z-index: 1;
  bottom: -20px;
  right: -50px;
  width: 80%;
  transform: rotate(-15deg);
  overflow: hidden;
}
.skeleton-title {
  margin-top: 8px;
}
</style>
