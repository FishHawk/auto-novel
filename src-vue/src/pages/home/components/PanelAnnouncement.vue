<script lang="ts" setup>
import { useIsDesktop } from '@/data/util';
const isDesktop = useIsDesktop(600);
</script>

<template>
  <n-h2 prefix="bar">公告</n-h2>
  <n-space :wrap="false">
    <img v-if="isDesktop" src="/qq.png" width="130" />
    <n-ul>
      <n-li>
        新建了个交流群：819513328，无论你是想上传自己收集的资源，还是单纯想讨论轻小说，都欢迎加群。加群验证的答案是“绿色”。
      </n-li>
      <n-li>
        <b>文库页面现在开放上传了</b
        >，想翻译小说文档的朋友请尽量在小说页面上传。
      </n-li>
      <n-li>
        如何使用翻译插件和GPT翻译器请参考
        <n-a href="/how-to-use" target="_blank">使用说明</n-a>。
        Alphapolis和Pixiv是我用自己的cookie垫进去的，如果加载不了就是我cookie过期了，请提醒我更新。
      </n-li>
    </n-ul>
  </n-space>
</template>
