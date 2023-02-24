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
  width: 800px;
  margin: 0 auto;
  padding-left: 24px;
  padding-right: 24px;
  padding-bottom: 48px;
}
a {
  text-decoration: none;
}
.n-h:first-child {
  margin: var(--n-margin);
}

@media only screen and (max-width: 600px) {
  .content {
    width: auto;
    padding-left: 10px;
    padding-right: 10px;
    padding-bottom: 48px;
  }
  .on-desktop {
    display: none;
  }
}

@media only screen and (min-width: 600px) {
  .on-mobile {
    display: none;
  }
}
</style>
