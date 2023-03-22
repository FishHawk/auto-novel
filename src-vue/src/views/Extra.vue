<script lang="ts" setup>
import { onMounted, ref } from 'vue';
const iframe = ref();

var lastHeight = 0;
onMounted(() => {
  window.setInterval(() => {
    if (iframe.value) {
      const height =
        iframe.value.contentWindow.document.documentElement.scrollHeight;
      if (lastHeight !== height) {
        lastHeight = height;
        iframe.value.height =
          iframe.value.contentWindow.document.documentElement.scrollHeight;
      }
    }
  }, 200);
});
</script>

<template>
  <MainLayout>
    <iframe
      ref="iframe"
      src="/files-extra"
      scrolling="no"
      title="description"
      frameBorder="0"
      width="100%"
    />
  </MainLayout>
</template>
