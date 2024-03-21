<script lang="ts" setup>
const route = useRoute();
const router = useRouter();
const navToContent = () => {
  const from = route.query.from;
  if (typeof from === 'string' && from.startsWith('/')) {
    router.push(from);
  } else {
    router.push('/');
  }
};
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
          <sign-in-form @sign-in="navToContent" />
        </div>
      </n-tab-pane>

      <n-tab-pane name="signup" tab="注册">
        <div style="padding: 20px">
          <sign-up-form @sign-up="navToContent" />
        </div>
      </n-tab-pane>
    </n-tabs>
  </n-card>
</template>
