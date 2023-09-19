<script lang="ts" setup>
import { diffChars } from 'diff';
defineProps<{
  diff: {
    jp: string;
    zhOld?: string;
    zhNew: string;
  };
}>();
</script>

<template>
  <div>
    <div>{{ diff.jp }}</div>
    <n-p>
      <template v-for="c in diffChars(diff.zhOld ?? '', diff.zhNew)">
        <ins v-if="c.added">{{ c.value }}</ins>
        <del v-else-if="c.removed">{{ c.value }}</del>
        <template v-else>{{ c.value }}</template>
      </template>
    </n-p>
  </div>
</template>

<style scoped>
ins {
  background-color: #e6ffec;
  text-decoration: none;
}
del {
  background-color: #ffebe9;
}
</style>
