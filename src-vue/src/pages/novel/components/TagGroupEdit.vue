<script lang="ts" setup>
import { ref } from 'vue';

const props = defineProps<{
  tags: string[];
}>();

const tagToAdd = ref('');

function removeTag(tag: string) {
  const index = props.tags.indexOf(tag);
  if (index !== -1) {
    props.tags.splice(index, 1);
  }
}

function addTag(tag: string) {
  if (props.tags.indexOf(tag) === -1 && tag) {
    props.tags.push(tag);
    tagToAdd.value = '';
  }
}
</script>

<template>
  <TagGroup :tags="tags" closable @close="removeTag($event)" />
  <n-input-group style="margin-top: 20px">
    <n-input
      v-model:value="tagToAdd"
      placeholder="请输入要添加的标签..."
      @keyup.enter="addTag(tagToAdd)"
    />
    <n-button type="primary" @click="addTag(tagToAdd)"> 添加 </n-button>
  </n-input-group>
</template>
