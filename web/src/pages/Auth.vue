<script setup lang="ts">
import { useWebKit } from '@novelia/web-kit';

const props = defineProps<{ from?: string }>();
const router = useRouter();
const { whoami } = useWebKit();
watch(
  () => whoami.value.isSignedIn,
  (signedIn) => {
    if (!signedIn) return;
    const from = props.from;
    const target =
      typeof from === 'string' &&
      from.startsWith('/') &&
      !from.startsWith('//') &&
      router.resolve(from).name !== 'auth'
        ? from
        : '/';
    void router.replace(target);
  },
  { immediate: true },
);
</script>

<template>
  <div class="layout-content">
    <n-h1>统一身份认证</n-h1>
    <n-p>请点击右上角「登录/注册」，登录后会自动返回之前的页面。</n-p>
  </div>
</template>
