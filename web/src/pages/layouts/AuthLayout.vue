<script lang="ts" setup>
import bannerUrl from '@/image/banner.webp';
</script>

<template>
  <n-layout>
    <div
      :style="{ background: `rgba(0, 0, 0, .75) url(${bannerUrl})` }"
      style="
        position: fixed;
        width: 100%;
        height: 100%;
        background-blend-mode: darken;
      "
    >
      <div style="width: 100%; height: 100%; backdrop-filter: blur(4px)" />
    </div>

    <div style="width: min(400px, calc(100% - 16px)); margin: 50px auto">
      <n-h1
        style="
          text-align: center;
          font-size: 3em;
          color: white;
          filter: drop-shadow(0.05em 0.05em black);
        "
      >
        轻小说机翻机器人
      </n-h1>

      <div style="margin-top: 50px">
        <router-view />
      </div>
    </div>
  </n-layout>
</template>
