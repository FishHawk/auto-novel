<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import { formatError } from '@/data/api/client';

const message = useMessage();

const props = defineProps<{
  label: string;
  allowSendEmail: () => boolean;
  sendEmail: () => Promise<any>;
}>();

type VerifyState =
  | { state: 'sending' }
  | { state: 'cooldown'; seconds: number }
  | undefined;

const verifyState = ref<VerifyState>(undefined);

const verifyButtonLabel = computed(() => {
  if (verifyState.value === undefined) {
    return props.label;
  } else if (verifyState.value.state === 'sending') {
    return '发送中';
  } else {
    return `${verifyState.value.seconds}秒冷却`;
  }
});

async function realSendEmail() {
  if (verifyState.value !== undefined) return;
  if (!props.allowSendEmail) return;

  verifyState.value = { state: 'sending' };
  await props
    .sendEmail()
    .then(() => {
      verifyState.value = { state: 'cooldown', seconds: 60 };
      message.info('邮件已发送');

      const timer = window.setInterval(() => {
        if (
          verifyState.value?.state === 'cooldown' &&
          verifyState.value.seconds > 0
        ) {
          verifyState.value.seconds--;
        } else {
          verifyState.value = undefined;
          window.clearInterval(timer);
        }
      }, 1000);
    })
    .catch(async (e) => {
      verifyState.value = undefined;
      message.error('邮件发送失败:' + (await formatError(e)));
    });
}
</script>

<template>
  <n-button
    type="primary"
    :disabled="verifyState !== undefined"
    @click="realSendEmail()"
    style="width: 100px"
  >
    {{ verifyButtonLabel }}
  </n-button>
</template>
