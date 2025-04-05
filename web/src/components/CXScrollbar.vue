<script setup lang="ts">
import { ScrollbarInst } from 'naive-ui';

const scrollRef = ref<ScrollbarInst>();
let point = { x: 0, isDrag: false };
const handleMouseDown = (e: MouseEvent) => {
  e.preventDefault();
  point.x = e.clientX;
  point.isDrag = false;

  const handleMouseMove = (e: MouseEvent) => {
    e.preventDefault();
    const deltaX = e.clientX - point.x;
    point.x = e.clientX;
    point.isDrag = true;
    scrollRef.value!.scrollBy(-deltaX, 0);
  };

  const handleMouseUp = () => {
    document.removeEventListener('mousemove', handleMouseMove);
    document.removeEventListener('mouseup', handleMouseUp);
  };

  document.addEventListener('mousemove', handleMouseMove);
  document.addEventListener('mouseup', handleMouseUp);
};

const handleClick = (e: MouseEvent) => {
  if (point.isDrag) {
    e.stopPropagation();
    point.isDrag = false;
  }
};
</script>

<template>
  <n-scrollbar x-scrollable ref="scrollRef" @click.capture="handleClick">
    <div @mousedown.left="handleMouseDown">
      <slot />
    </div>
  </n-scrollbar>
</template>

<style></style>
