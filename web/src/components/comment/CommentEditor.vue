<script lang="ts" setup>
import { Toast } from '@novelia/web-kit';

import { CommentRepo } from '@/repos';

import MarkdownHelpDialog from '../markdown/MarkdownHelpDialog.vue';
import CommentRulesNotice from './CommentRulesNotice.vue';

const props = defineProps<{
  site: string;
  draftId: string;
  parent?: number;
  placeholder?: string;
}>();

const emit = defineEmits<{
  cancel: [];
  replied: [];
}>();

const content = ref('');

const reply = async () => {
  if (content.value.length === 0) {
    Toast.info('回复内容不能为空');
    return;
  }

  await Toast.promise(
    CommentRepo.createComment(props.site, {
      content: content.value,
      ...(props.parent === undefined ? {} : { rootId: props.parent }),
    }).then(() => {
      content.value = '';
      emit('replied');
    }),
    { success: '回复发布成功', error: '回复发布失败' },
  );
};
</script>

<template>
  <div>
    <CommentRulesNotice class="mb-3" />
    <MarkdownEditor
      mode="comment"
      :draft-id="draftId"
      v-model:value="content"
      :placeholder="placeholder"
      :rows="4"
      :maxlength="1000"
      :ui="{ textarea: 'max-h-[19.5rem] overflow-y-auto' }"
    />
    <div class="mt-3 flex flex-wrap items-center justify-between gap-3">
      <MarkdownHelpDialog />
      <div class="flex items-center gap-2">
        <c-button
          class="comment-button comment-button--outline"
          label="取消"
          :round="false"
          @action="emit('cancel')"
        />
        <c-button
          class="comment-button comment-button--primary"
          label="发布"
          require-login
          :round="false"
          type="primary"
          @action="reply()"
        />
      </div>
    </div>
  </div>
</template>
