<script lang="ts" setup>
import { CommentOutlined, MoreVertOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { Comment1 } from '@/model/Comment';

const { comment, topLevel } = defineProps<{
  comment: Comment1;
  topLevel?: boolean;
}>();

const emit = defineEmits<{
  hide: [Comment1];
  unhide: [Comment1];
  copy: [Comment1];
  reply: [Comment1];
}>();

const { whoami } = Locator.authRepository();
const options = computed(() => {
  const options = [
    {
      label: '复制',
      key: 'copy',
    },
  ];
  if (whoami.value.asMaintainer) {
    if (comment.hidden) {
      options.push({
        label: '解除隐藏',
        key: 'unhide',
      });
    } else {
      options.push({
        label: '隐藏',
        key: 'hide',
      });
    }
  }
  return options;
});

const handleSelect = (key: string) => {
  if (key === 'copy') {
    emit('copy', comment);
  } else if (key === 'hide') {
    emit('hide', comment);
  } else if (key === 'unhide') {
    emit('unhide', comment);
  }
};
</script>

<template>
  <n-flex align="center" :size="0">
    <n-text>
      <b>{{ comment.user.username }}</b>
    </n-text>
    <n-text depth="3" style="font-size: 12px; margin-left: 12px">
      <n-time :time="comment.createAt * 1000" type="relative" />
    </n-text>

    <div style="flex: 1" />

    <c-button
      v-if="topLevel && whoami.allowAdvancedFeatures"
      label="回复"
      :icon="CommentOutlined"
      require-login
      quaternary
      type="tertiary"
      size="tiny"
      style="margin-right: 4px"
      @action="emit('reply', comment)"
    />

    <n-dropdown trigger="click" :options="options" @select="handleSelect">
      <n-button circle quaternary type="tertiary" size="tiny">
        <n-icon :component="MoreVertOutlined" />
      </n-button>
    </n-dropdown>
  </n-flex>

  <n-card embedded :bordered="false" size="small" style="margin-top: 2px">
    <n-text v-if="comment.hidden" depth="3">[隐藏]</n-text>
    <MarkdownView
      v-else
      mode="comment"
      :source="comment.content"
      style="margin-top: -1em; margin-bottom: -1em"
    />
  </n-card>
</template>
