<script lang="ts" setup>
defineProps<{ to: string }>();
</script>
<template>
  <router-link :to="to" #="{ navigate, href }" custom>
    <n-a :href="href" @click="navigate">
      <slot />
    </n-a>
  </router-link>
</template>
