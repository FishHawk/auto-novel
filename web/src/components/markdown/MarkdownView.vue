<script lang="ts" setup>
import MarkdownItAnchor from 'markdown-it-anchor';
import MarkdownIt from 'markdown-it';
import { spoiler } from '@mdit/plugin-spoiler';
import { container } from '@mdit/plugin-container';
import { NRate } from 'naive-ui';
import { render } from 'vue';

const props = defineProps<{
  mode: 'article' | 'comment';
  source: string;
}>();

const getRules = (mode: 'article' | 'comment') => {
  if (mode === 'article') {
    return [];
  } else if (mode === 'comment') {
    return [
      'backticks',
      'blockquote',
      'code',
      'entity',
      'escape',
      'fence',
      'heading',
      'hr',
      'image',
      'lheading',
      'reference',
      'table',
    ];
  } else {
    return mode satisfies never;
  }
};

const md = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
})
  .use(MarkdownItAnchor)
  // spoiler会在点击时切换高亮（未点击时是hover高亮）
  .use(spoiler, {
    tag: 'span',
    attrs: [
      ['data-hide', 'true'],
      [
        'onclick',
        "this.dataset.hide = this.dataset.hide === 'true' ? 'false' : 'true'",
      ],
      ['tabindex', '-1'],
    ],
  })
  .use(container, {
    name: 'details',
    validate: (params) => params.trim().split(' ', 2)[0] === 'details',
    openRender: (tokens, index, _options) => {
      const info = tokens[index].info.trim().slice(8).trim();
      return `<p><details dir="auto"><summary>${info}</summary>`;
    },
    closeRender: (tokens, idx) => {
      return '</details></p>';
    },
  })
  .use(container, {
    name: 'star',
    validate: (params) => params.trim().split(' ', 2)[0] === 'star',
    openRender: (tokens, index, _options) => {
      const info = tokens[index].info.trim().slice(5).trim();
      const starValue = !isNaN(Number(info)) && info !== '' ? info : '0';
      return `<p><div class="starRating" data-star=${starValue}></div></p>`;
    },
  })
  .disable(getRules(props.mode));

// 将 class=starRating 渲染为rating 组件
onMounted(() => {
  const starElements = document.querySelectorAll('.starRating');
  starElements.forEach((starEl) => {
    const starValue = starEl.getAttribute('data-star') || '0';
    const vnode = h(NRate, {
      value: Number(starValue),
      readonly: true,
      allowHalf: true,
      color: '#4fb233',
    });

    const mountPoint = document.createElement('p');
    starEl.replaceWith(mountPoint);
    render(vnode, mountPoint);
  });
});

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

const defaultRender =
  md.renderer.rules.link_open ||
  function (tokens, idx, options, env, self) {
    return self.renderToken(tokens, idx, options);
  };

md.renderer.rules.link_open = function (tokens, idx, options, env, self) {
  const href = tokens[idx].attrGet('href');
  if (href && !href.startsWith('#')) tokens[idx].attrSet('target', '_blank');

  return defaultRender(tokens, idx, options, env, self);
};
</script>

<template>
  <n-el tag="div" class="markdown" v-html="md.render(source)" />
</template>

<style>
.markdown {
  overflow-wrap: break-word;
  word-break: break-word;
}

.markdown a,
.markdown p,
.markdown ul,
.markdown ol,
.markdown li {
  transition: color 0.3s var(--cubic-bezier-ease-in-out);
  line-height: var(--line-height);
  font-size: var(--font-size);
}

.markdown a {
  text-decoration: none;
  color: var(--primary-color);
}
.markdown p {
  margin: 16px 0 16px 0;
  color: var(--text-color-2);
}
.markdown ul,
.markdown ol {
  padding: 0 0 0 2em;
}
.markdown li {
  margin: 0.25em 0 0 0;
  color: var(--text-color-2);
}
.markdown code {
  transition:
    color 0.3s var(--cubic-bezier-ease-in-out) background-color 0.3s
      var(--cubic-bezier-ease-in-out),
    border-color 0.3s var(--cubic-bezier-ease-in-out);
  padding: 0.05em 0.35em 0 0.35em;
  font-size: 0.9em;
  color: var(--text-color-2);
  background-color: var(--code-color);
  border-radius: var(--border-radius-small);
  border: 1px solid #0000;
  line-height: 1.4;
  box-sizing: border-box;
  display: inline-block;
}
.markdown img {
  max-width: 100%;
}
.markdown table {
  display: block;
  overflow-x: auto;
  border-spacing: 0;
  border-collapse: collapse;
}
.markdown th {
  white-space: nowrap;
  background-color: var(--action-color);
}
.markdown th,
.markdown td {
  padding: 12px;
  border-bottom: 1px solid var(--divider-color);
}
.markdown tr th:not(:last-child),
.markdown td:not(:last-child) {
  border-right: 1px solid var(--divider-color);
}

.markdown span[data-hide] {
  background-color: var(--text-color-1);
  transition: color ease 0.2s;
  padding: 0.05em 0.2em;
}

.markdown span[data-hide='true'] {
  color: transparent;
}

.markdown span[data-hide='false'] {
  color: var(--body-color);
}

.markdown span[data-hide='true']:hover,
.markdown span[data-hide='true']:focus {
  color: var(--body-color);
}
</style>
