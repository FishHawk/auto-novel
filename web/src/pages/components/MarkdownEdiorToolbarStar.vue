<script setup lang="ts">
const star = ref(5);
const emit = defineEmits<{
  click: [Number];
}>();
</script>

<template>
  <n-flex vertical style="padding: 10px">
    <n-rate v-model:value="star" color="#4fb233" allow-half clearable />
    <n-button @click="$emit('click', star)" size="small">确定</n-button>
  </n-flex>
</template>
