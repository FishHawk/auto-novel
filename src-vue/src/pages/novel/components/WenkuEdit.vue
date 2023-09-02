<script lang="ts" setup>
import { ref } from 'vue';
import { useMessage } from 'naive-ui';
import { UploadFilled } from '@vicons/material';

import { atLeastMaintainer, useAuthInfoStore } from '@/data/stores/authInfo';
import { ApiWenkuNovel, WenkuMetadataDto } from '@/data/api/api_wenku_novel';

const props = defineProps<{
  id: string;
  metadata: WenkuMetadataDto;
}>();

const authInfoStore = useAuthInfoStore();

const message = useMessage();

const isSubmitting = ref(false);

async function submit() {
  if (isSubmitting.value) return;
  isSubmitting.value = true;

  if (!atLeastMaintainer(authInfoStore.role)) {
    message.info('权限不够');
    return;
  }

  const patch = {
    title: props.metadata.title,
    titleZh: props.metadata.titleZh,
    titleZhAlias: props.metadata.titleZhAlias,
    cover: props.metadata.cover,
    coverSmall: props.metadata.coverSmall,
    authors: props.metadata.authors,
    artists: props.metadata.artists,
    keywords: props.metadata.keywords,
    introduction: props.metadata.introduction,
  };

  const result = await ApiWenkuNovel.patchMetadata(props.id, patch);
  isSubmitting.value = false;
  if (result.ok) {
    message.success('提交成功');
  } else {
    message.error('提交失败：' + result.error.message);
  }
}
</script>

<template>
  <n-p>标题</n-p>
  <n-input v-model:value="metadata.title" />

  <n-p>中文标题</n-p>
  <n-input v-model:value="metadata.titleZh" />

  <n-p>别名</n-p>
  <TagGroupEdit :tags="metadata.titleZhAlias" />

  <n-p>作者</n-p>
  <TagGroupEdit :tags="metadata.authors" />

  <n-p>插图</n-p>
  <TagGroupEdit :tags="metadata.artists" />

  <n-p>标签</n-p>
  <TagGroupEdit :tags="metadata.keywords" />

  <n-p>简介</n-p>
  <n-input
    v-model:value="metadata.introduction"
    :autosize="{
      minRows: 3,
      maxRows: 10,
    }"
    type="textarea"
  />

  <n-button
    round
    size="large"
    type="primary"
    class="float"
    :loading="isSubmitting"
    @click="submit()"
  >
    <template #icon>
      <n-icon><UploadFilled /></n-icon>
    </template>
    提交
  </n-button>
</template>

<style scoped>
.float {
  position: fixed;
  right: 40px;
  bottom: 40px;
  box-shadow: rgb(0 0 0 / 12%) 0px 2px 8px 0px;
}
</style>
