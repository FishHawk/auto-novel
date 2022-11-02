<script setup lang="ts">
import { ref } from 'vue';

defineProps<{ loading: boolean }>();
defineEmits<{ (e: 'onSearch', url: string): void }>();

const input_content = ref('');
</script>

<template>
  <div class="search-bar">
    <el-input
      ref="input"
      class="search-bar-input"
      v-model="input_content"
      size="large"
      placeholder="请输入小说链接..."
      autofocus="true"
    />
    <el-button
      @click="$emit('onSearch', input_content)"
      type="primary"
      class="search-bar-button"
      color="#2c3e50"
      :loading="loading"
    >
      查询
    </el-button>
  </div>
</template>

<style scoped>
.search-bar {
  display: inline-block;
  border: 2px solid #2c3e50;
}

.search-bar-input {
  width: 600px;
  height: 60px;
  font-size: 20px;

  --el-color-primary: #2c3e50;
  --el-input-border-color: transparent;
  --el-input-hover-border-color: transparent;
  --el-input-clear-hover-color: transparent;
  --el-input-focus-border-color: transparent;
}

.search-bar-button {
  width: 120px;
  height: 60px;
  font-size: 20px;
  border-radius: 0px;
}
</style>
