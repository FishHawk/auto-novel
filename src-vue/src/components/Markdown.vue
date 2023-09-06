<script lang="ts" setup>
import MarkdownIt from 'markdown-it/lib';
import { useThemeVars } from 'naive-ui';

defineProps<{ source: string }>();

const md = new MarkdownIt();
const vars = useThemeVars();
</script>

<template>
  <div class="md" v-html="md.render(source)" />
</template>

<style>
.md a {
  transition: color 0.3s v-bind('vars.cubicBezierEaseInOut');
  cursor: pointer;
  text-decoration: none;
  color: v-bind('vars.primaryColor');
}
.md p {
  transition: color 0.3s v-bind('vars.cubicBezierEaseInOut');
  box-sizing: border-box;
  margin: 16px 0 16px 0;
  font-size: v-bind('vars.fontSize');
  line-height: v-bind('vars.lineHeight');
  color: v-bind('vars.textColor2');
}
.md ul,
ol {
  font-size: v-bind('vars.fontSize');
  padding: 0 0 0 2em;
}
.md li {
  transition: color 0.3s v-bind('vars.cubicBezierEaseInOut');
  line-height: v-bind('vars.lineHeight');
  margin: 0.25em 0 0 0;
  margin-bottom: 0;
  color: v-bind('vars.textColor2');
}
.md code {
  padding: 2px 4px;
  font-size: 90%;
  color: #c7254e;
  background-color: #f9f2f4;
}
.md img {
  max-width: 100%;
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
