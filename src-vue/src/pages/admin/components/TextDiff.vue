<script lang="ts" setup>
defineProps<{
  diff: {
    jp: string;
    zhOld?: string;
    zhNew: string;
  };
}>();
</script>

<template>
  <div>
    <div>{{ diff.jp }}</div>
    <div class="text-old">{{ diff.zhOld ?? '无' }}</div>
    <div class="text-new">{{ diff.zhNew }}</div>
  </div>
</template>

<style scoped>
.text-new {
  background-color: #e6ffec;
}
.text-old {
  background-color: #ffebe9;
}
</style>
