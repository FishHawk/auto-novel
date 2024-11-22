<script lang="ts" setup>
import MarkdownItAnchor from 'markdown-it-anchor';
import MarkdownIt from 'markdown-it';

defineProps<{ source: string }>();

const md = new MarkdownIt({
  breaks: true,
  linkify: true,
}).use(MarkdownItAnchor);

md.linkify.add('http:', {
  validate: function (text, pos, self) {
    const tail = text.slice(pos);
    if (!self.re.customHTTP) {
      self.re.customHTTP =
        /(\/\/[a-zA-Z0-9\-\.]+\.[a-zA-Z]{2,6}(?:[/?][a-zA-Z0-9-_/?=&%*#+]+)?)/g;
    }

    return tail.match(self.re.customHTTP)?.[0].length || 0;
  },
});

// Remember the old renderer if overridden, or proxy to the default renderer.
const defaultRender =
  md.renderer.rules.link_open ||
  function (tokens, idx, options, env, self) {
    return self.renderToken(tokens, idx, options);
  };

md.renderer.rules.link_open = function (tokens, idx, options, env, self) {
  // Add a new `target` attribute, or replace the value of the existing one.
  tokens[idx].attrSet('target', '_blank');

  // Pass the token to the default renderer.
  return defaultRender(tokens, idx, options, env, self);
};

const vars = useThemeVars();
</script>

<template>
  <div class="markdown" v-html="md.render(source)" />
</template>

<style>
.markdown {
  overflow-wrap: break-word;
  word-break: break-word;
}
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
  transition:
    color 0.3s v-bind('vars.cubicBezierEaseInOut'),
    background-color 0.3s v-bind('vars.cubicBezierEaseInOut'),
    border-color 0.3s v-bind('vars.cubicBezierEaseInOut');
  padding: 0.05em 0.35em 0 0.35em;
  font-size: 0.9em;
  color: v-bind('vars.textColor2');
  background-color: v-bind('vars.codeColor');
  border-radius: v-bind('vars.borderRadiusSmall');
  border: 1px solid #0000;
  line-height: 1.4;
  box-sizing: border-box;
  display: inline-block;
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
  background-color: v-bind('vars.actionColor');
}
.markdown th,
.markdown td {
  padding: 12px;
  border-bottom: 1px solid v-bind('vars.dividerColor');
}
.markdown tr th:not(:last-child),
.markdown td:not(:last-child) {
  border-right: 1px solid v-bind('vars.dividerColor');
}
</style>
