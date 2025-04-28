<script lang="ts" setup>
import { VueUtil } from '@/util';
import { diffChars } from 'diff';

defineProps<{ zhOld?: string; zhNew: string }>();
</script>

<template>
  <div>
    <n-p>
      <template
        v-for="(c, idx) in diffChars(zhOld ?? '', zhNew)"
        :key="VueUtil.buildKey(idx, c.value)"
      >
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
