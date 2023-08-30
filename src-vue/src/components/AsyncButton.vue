<script lang="ts" setup>
import { ref } from 'vue';

const { onAsyncClick } = defineProps<{
  onAsyncClick: () => Promise<void>;
}>();

const loading = ref(false);

async function onClick() {
  if (loading.value) return;
  loading.value = true;
  await onAsyncClick();
  loading.value = false;
}
</script>

<template>
  <n-button :loading="loading" @click="onClick()"><slot /></n-button>
</template>
