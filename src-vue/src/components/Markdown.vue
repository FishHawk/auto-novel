<script lang="ts" setup>
import MarkdownIt from 'markdown-it/lib';

defineProps<{ source: string }>();

const md = new MarkdownIt();
</script>

<template>
  <div class="md" v-html="md.render(source)" />
</template>

<style>
/* Markdown */
.md img {
  max-width: 100%;
}
.md code {
  padding: 2px 4px;
  font-size: 90%;
  color: #c7254e;
  background-color: #f9f2f4;
}

.md table {
  border-spacing: 0;
  border-collapse: collapse;
}
.md th {
  white-space: nowrap;
  background-color: rgb(250, 250, 252);
}
.md th,
td {
  padding: 12px;
  border-bottom: 1px solid rgb(239, 239, 245);
}
.md tr th:not(:last-child),
td:not(:last-child) {
  border-right: 1px solid rgb(239, 239, 245);
}
</style>
