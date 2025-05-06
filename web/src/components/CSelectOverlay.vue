<script lang="ts" setup>
const checked = defineModel<boolean>('checked', { default: false });
</script>

<template>
  <n-el
    tag="div"
    :class="{
      overlay: true,
      'overlay-unselected': !checked,
      'overlay-selected': checked,
    }"
    @click="checked = !checked"
  />
</template>

<style scoped>
.overlay {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  border-radius: 2px;
}
.overlay-unselected:hover {
  border: 1px dashed var(--text-color-3);
}
.overlay-selected {
  border: 2px solid var(--primary-color);
}
</style>
