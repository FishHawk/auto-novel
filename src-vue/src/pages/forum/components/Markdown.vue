<script lang="ts" setup>
import MarkdownIt from 'markdown-it/lib';
import { useThemeVars } from 'naive-ui';

defineProps<{ source: string }>();

const md = new MarkdownIt();
const vars = useThemeVars();
</script>

<template>
  <div class="markdown" v-html="md.render(source)" />
</template>

<style>
.markdown a {
  transition: color 0.3s v-bind('vars.cubicBezierEaseInOut');
  cursor: pointer;
  text-decoration: none;
  color: v-bind('vars.primaryColor');
}
.markdown p {
  transition: color 0.3s v-bind('vars.cubicBezierEaseInOut');
  box-sizing: border-box;
  margin: 16px 0 16px 0;
  font-size: v-bind('vars.fontSize');
  line-height: v-bind('vars.lineHeight');
  color: v-bind('vars.textColor2');
}
.markdown ul,
.markdown ol {
  font-size: v-bind('vars.fontSize');
  padding: 0 0 0 2em;
}
.markdown li {
  transition: color 0.3s v-bind('vars.cubicBezierEaseInOut');
  line-height: v-bind('vars.lineHeight');
  margin: 0.25em 0 0 0;
  margin-bottom: 0;
  color: v-bind('vars.textColor2');
}
.markdown code {
  padding: 2px 4px;
  font-size: 90%;
  color: #c7254e;
  background-color: #f9f2f4;
}
.markdown img {
  max-width: 100%;
}
.markdown table {
  border-spacing: 0;
  border-collapse: collapse;
}
.markdown th {
  white-space: nowrap;
  background-color: rgb(250, 250, 252);
}
.markdown th,
.markdown td {
  padding: 12px;
  border-bottom: 1px solid rgb(239, 239, 245);
}
.markdown tr th:not(:last-child),
.markdown td:not(:last-child) {
  border-right: 1px solid rgb(239, 239, 245);
}
</style>
