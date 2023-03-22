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
