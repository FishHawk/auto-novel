<template>
  <n-message-provider>
    <router-view :key="$route.path" />
  </n-message-provider>
</template>

<style>
#app {
  width: 100;
}
.content {
  width: 730px;
  margin: 0 auto;
  margin-bottom: 40px;
}
a {
  text-decoration: none;
}
.n-h:first-child {
  margin: var(--n-margin);
}
</style>
