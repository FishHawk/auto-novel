<script lang="ts" setup>
const subPages = [
  {
    name: '术语表工作区',
    description: '辅助制作术语表。',
    href: '/workspace/katakana',
  },
  {
    name: 'GPT工作区',
    description: '使用GPT翻译小说。',
    href: '/workspace/gpt',
  },
  {
    name: 'Sakura工作区',
    description: '使用Sakura翻译小说。',
    href: '/workspace/sakura',
  },
];
</script>

<template>
  <div class="layout-content">
    <n-h1>工作区</n-h1>

    <n-ul>
      <n-li v-for="page of subPages">
        <RouterNA :to="page.href">
          <b>{{ page.name }}</b>
        </RouterNA>
        {{ page.description }}
      </n-li>
    </n-ul>

    <local-volume-list-composite />
  </div>
</template>
