<script lang="ts" setup>
import { CommentRepository } from '@/data/api';
import { doAction } from '@/pages/util';

const { site, parent } = withDefaults(
  defineProps<{
    site: string;
    parent?: string;
    placeholder?: string;
  }>(),
  { parent: undefined },
);

const emit = defineEmits<{
  replied: [];
}>();

const message = useMessage();

const content = ref('');

const reply = async () => {
  if (content.value.length === 0) {
    message.info('回复内容不能为空');
    return;
  }

  await doAction(
    CommentRepository.createComment({
      site,
      parent,
      content: content.value,
    }).then(() => {
      content.value = '';
      emit('replied');
    }),
    '回复发布',
    message,
  );
};
</script>

<template>
  <n-input
    v-model:value="content"
    type="textarea"
    :placeholder="placeholder"
    :autosize="{
      minRows: 2,
      maxRows: 10,
    }"
    maxlength="1000"
    show-count
    style="margin-top: 10px"
    :input-props="{ spellcheck: false }"
  />
  <c-button
    label="发布"
    require-login
    :round="false"
    type="primary"
    @action="reply()"
    style="margin-top: 10px"
  />
</template>
