<script lang="ts" setup>
import { CommentRepository } from '@/data/api';
import { doAction } from '@/pages/util';

const props = defineProps<{
  site: string;
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
const showMarkdownEditorModal = ref(false);
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
  <n-flex>
    <c-button
      label="发布"
      require-login
      :round="false"
      type="primary"
      @action="reply()"
      style="margin-top: 10px"
    />
    <c-button
      label="MD编辑器"
      require-login
      :round="false"
      @action="showMarkdownEditorModal = true"
      style="margin-top: 10px"
    />
  </n-flex>

  <c-drawer-down v-model:show="showMarkdownEditorModal" title="MD编辑器">
    <template #action>
      <c-button
        label="发布"
        require-login
        :round="false"
        type="primary"
        @action="reply()"
        style="margin-top: 10px"
      />
      <c-button
        label="关闭"
        require-login
        :round="false"
        @action="showMarkdownEditorModal = false"
        style="margin-top: 10px"
      />
    </template>
    <markdown-input
      draft-id="Comment"
      v-model:value="content"
      placeholder="请输入正文"
      maxlength="1000"
      style="width: 100%"
    />
  </c-drawer-down>
</template>
