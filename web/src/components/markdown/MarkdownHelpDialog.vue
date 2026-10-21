<script setup lang="ts">
import { XButton, XDialog } from '@novelia/web-kit';
import { HelpOutlineOutlined } from '@vicons/material';

const open = ref(false);

const features = [
  { name: '粗体', syntax: '**粗体文字**' },
  { name: '斜体', syntax: '*斜体文字*' },
  { name: '删除线', syntax: '~~删除的文字~~' },
  { name: '行内代码', syntax: '`代码`' },
  { name: '链接', syntax: '[链接文字](https://example.com)' },
  { name: '自动链接', syntax: 'https://example.com' },
  { name: '无序列表', syntax: '- 列表项' },
  { name: '有序列表', syntax: '1. 列表项' },
  { name: '换行', syntax: '直接换行' },
  { name: '剧透', syntax: '!!剧透内容!!' },
  { name: '星级评分', syntax: '::: star 4.5' },
  { name: '折叠内容', syntax: '::: details 点击展开\n折叠内容\n:::' },
];
</script>

<template>
  <XDialog
    v-model:open="open"
    title="Markdown 语法帮助"
    description="以下是评论支持的基本语法。"
    class="w-[42rem]"
    :ui="{
      header:
        'border-b border-divider px-5 py-4 pr-14 sm:px-6 sm:py-4 sm:pr-14',
      title: 'font-semibold',
      description: 'mt-1',
      body: 'p-4 sm:p-6',
    }"
    style="max-height: min(80dvh, calc(100dvh - 2rem))"
  >
    <template #trigger>
      <XButton
        variant="ghost"
        size="xs"
        class="hover:bg-divider/40 hover:text-ink"
      >
        <HelpOutlineOutlined class="size-4" aria-hidden="true" />
        Markdown 语法帮助
      </XButton>
    </template>
    <div>
      <div
        class="divide-y divide-divider sm:divide-y-0 sm:overflow-hidden sm:rounded-md sm:border sm:border-border"
      >
        <div
          class="hidden grid-cols-[10.5rem_minmax(0,1fr)] bg-paper text-xs font-semibold text-muted sm:grid"
        >
          <span class="px-3 py-2.5">功能</span>
          <span class="px-3 py-2.5">基本语法</span>
        </div>
        <section
          v-for="feature in features"
          :key="feature.name"
          class="grid gap-2 py-3 text-sm sm:grid-cols-[10.5rem_minmax(0,1fr)] sm:items-center sm:gap-0 sm:border-t sm:border-divider sm:py-0"
        >
          <div class="sm:px-3 sm:py-2.5">
            <h3 class="font-medium text-ink">
              {{ feature.name }}
            </h3>
          </div>
          <code
            class="block whitespace-pre-wrap rounded-sm bg-paper px-2 py-1.5 font-mono text-xs leading-5 text-ink sm:mx-3 sm:my-2"
          >
            {{ feature.syntax }}
          </code>
        </section>
      </div>

      <p class="mt-4 text-xs leading-5 text-muted">
        为保证内容安全，评论不支持直接嵌入 HTML。
      </p>
    </div>
  </XDialog>
</template>
