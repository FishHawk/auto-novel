<template>
  <n-message-provider>
    <router-view :key="$route.path" />
  </n-message-provider>
</template>

<style>
#app {
  width: 100;
}
a {
  text-decoration: none;
}
.n-h:first-child {
  margin: var(--n-margin);
}

@media only screen and (max-width: 600px) {
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
