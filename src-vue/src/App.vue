<template>
  <n-message-provider>
    <router-view :key="$route.path" />
  </n-message-provider>
</template>

<style>
body {
  font-family: 'IBMPlexSans', Arial, sans-serif;
}
a {
  text-decoration: none;
}
.n-h:first-child {
  margin: var(--n-margin);
}
.n-layout {
  overflow: visible;
}
.n-layout .n-layout-scroll-container {
  overflow-x: visible;
}

.text-2line {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
