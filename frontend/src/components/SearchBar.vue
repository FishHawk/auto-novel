<script setup lang="ts">
import { ref } from 'vue';
import { SearchHistory } from '../models/Book';

const props = defineProps<{ loading: boolean; histories: SearchHistory[] }>();
const emits = defineEmits<{ (e: 'onSearch', url: string): void }>();

const history_visible = ref(false);
const input_content = ref('');

function onFocus() {
  if (props.histories.length > 0) {
    history_visible.value = true;
  }
}
function onBlur() {
  history_visible.value = false;
}
function onClickHistory(history: SearchHistory) {
  input_content.value = history.url;
  emits('onSearch', history.url);
}
</script>

<template>
  <div class="search-bar">
    <el-popover
      placement="bottom-start"
      :visible="history_visible"
      :width="720"
      :show-arrow="false"
    >
      <template #reference>
        <el-input
          ref="input"
          class="search-bar-input"
          v-model="input_content"
          size="large"
          placeholder="请输入小说链接..."
          autofocus="true"
          @focus="onFocus"
          @blur="onBlur"
        />
      </template>
      <div>
        <p
          @click="onClickHistory(history)"
          v-for="history in histories"
          class="search-history"
        >
          {{ history.title }}
        </p>
      </div>
    </el-popover>

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

.search-history {
  width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
