<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { Component, ref } from 'vue';

import { useUserDataStore } from '@/data/stores/user_data';

type Props = { label?: string; icon?: Component; requireLogin?: boolean } & (
  | { async: true; onClick: () => Promise<any> }
  | { async?: false; onClick?: () => void }
);

const props = defineProps<Props>();

const userData = useUserDataStore();
const message = useMessage();

const running = ref(false);

const onClick = () => {
  if (props.requireLogin === true && !userData.isLoggedIn) {
    message.info('请先登录');
    return;
  }

  if (props.async === true) {
    if (running.value) return;
    running.value = true;
    props.onClick().finally(() => (running.value = false));
  } else {
    if (props.onClick) {
      props.onClick();
    }
  }
};
</script>

<template>
  <n-button round @click="onClick">
    <template v-if="icon && label" #icon>
      <n-icon :component="icon" />
    </template>
    {{ label }}
    <template v-if="icon && !label">
      <n-icon :component="icon" />
    </template>
  </n-button>
</template>
