<script lang="ts" setup>
import { useIsDesktop } from '@/data/util';
const isDesktop = useIsDesktop(600);
</script>

<template>
  <n-h2 prefix="bar">公告</n-h2>
  <n-space :wrap="false">
    <img v-if="isDesktop" src="/qq.png" width="130" />
    <n-ul>
      <n-li>有朋友整不明白术语表在哪改，现在术语表编辑功能也放在高级选项里面了。</n-li>
      <n-li>
        新建了个交流群：819513328，无论你是想上传自己收集的资源，还是单纯想讨论轻小说，都欢迎加群。加群验证的答案是“绿色”。
      </n-li>
      <n-li>
        <b>
          如何使用翻译插件请参考
          <n-a href="/wiki/extension" target="_blank">插件使用说明</n-a>
          。
        </b>
        Alphapolis和Pixiv是我用自己的帐号垫进去的，如果加载不了请提醒我更新。
      </n-li>
    </n-ul>
  </n-space>
</template>
