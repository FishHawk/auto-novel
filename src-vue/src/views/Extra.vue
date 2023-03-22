<script lang="ts" setup>
import { onMounted, ref } from 'vue';

const iframe = ref();
onMounted(() => {
  // $nextTick(() => {
  window.addEventListener('load', () => {
    console.log(iframe.value);
    // frameHeight = $refs.iframe.contentWindow.document.body.scrollHeight + 'px';
  });
  // })
});
</script>
<template>
  <MainLayout>
    <iframe
      ref="iframe"
      src="https://books.fishhawk.top/files-extra/"
      title="description"
      style="width: 100%; min-height: calc(100vh - 105px)"
      frameBorder="0"
    />
  </MainLayout>
</template>
