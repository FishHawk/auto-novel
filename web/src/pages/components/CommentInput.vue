<script lang="ts" setup>
import { CommentRepository } from '@/data/api';
import { doAction } from '@/pages/util';

const props = defineProps<{
  site: string;
  draftId: string;
  parent?: string;
  placeholder?: string;
}>();

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
      site: props.site,
      parent: props.parent,
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
  <markdown-input
    mode="comment"
    :draft-id="draftId"
    v-model:value="content"
    :placeholder="placeholder"
    :autosize="{ minRows: 4, maxRows: 12 }"
    maxlength="1000"
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
