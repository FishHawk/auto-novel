<script lang="ts" setup>
defineProps<{
  src: string;
  title: string;
}>();
</script>

<template>
  <n-card
    size="small"
    header-style="padding: 8px;"
    :bordered="false"
    style="box-shadow: 2px 2px 8px grey"
  >
    <template #cover>
      <img
        :src="src"
        alt="cover"
        style="aspect-ratio: 1 / 1.5; object-fit: cover"
      />
    </template>
  </n-card>

  <div
    style="height: 2.5em; padding: 8px; color: rgb(51, 54, 57)"
    class="text-2line"
  >
    {{ title }}
  </div>
</template>
