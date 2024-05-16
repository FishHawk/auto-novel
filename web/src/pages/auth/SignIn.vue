<script lang="ts" setup>
import { Locator } from '@/data';

const props = defineProps<{ from?: string }>();

const router = useRouter();

const { isSignedIn } = Locator.userDataRepository();

watch(
  isSignedIn,
  (isSignedIn) => {
    if (isSignedIn) {
      const from = props.from;
      if (from) {
        router.replace(from);
      } else {
        router.replace('/');
      }
    }
  },
  { immediate: true },
);
</script>

<template>
  <n-card content-style="padding: 0;">
    <n-tabs
      type="line"
      size="large"
      :tabs-padding="20"
      pane-style="padding: 0px;"
      animated
      style="width: 100%"
    >
      <n-tab-pane name="signin" tab="登录">
        <div style="padding: 20px">
          <sign-in-form />
        </div>
      </n-tab-pane>

      <n-tab-pane name="signup" tab="注册">
        <div style="padding: 20px">
          <sign-up-form />
        </div>
      </n-tab-pane>
    </n-tabs>
  </n-card>
</template>
