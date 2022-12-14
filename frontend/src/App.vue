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
}
a {
  text-decoration: none;
}
</style>
