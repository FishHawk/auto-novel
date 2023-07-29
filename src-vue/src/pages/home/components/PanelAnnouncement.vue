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
        根据群友测试，在使用GPT翻译的同时，打开ChatGPT主页有助于提高成功率，注意要挂在前台。
      </n-li>
      <n-li>
        新建了个交流群：819513328，无论你是想上传自己收集的资源，还是单纯想讨论轻小说，都欢迎加群。加群验证的答案是“绿色”。
      </n-li>
      <n-li>
        <b>
          如何使用翻译插件请参考
          <n-a href="/how-to-use" target="_blank">使用说明</n-a>
          。
        </b>
        Alphapolis和Pixiv是我用自己的帐号垫进去的，如果加载不了请提醒我更新。
      </n-li>
    </n-ul>
  </n-space>
</template>
