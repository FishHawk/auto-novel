<script lang="ts" setup>
import { useRoute } from 'vue-router';

const route = useRoute();
</script>

<template>
  <MainLayout>
    <n-h2 prefix="bar">说明</n-h2>

    <n-p>这是给大家反馈的地方，有什么意见或者Bug都可以发到这里。</n-p>

    <n-p>如果是特定页面有问题记得附上链接。</n-p>

    <n-p>
      你也可以到
      <n-a
        href="https://github.com/FishHawk/web-novel-ebook-generator"
        target="_blank"
      >
        GitHub
      </n-a>
      上提issue，或者在
      <n-a
        href="https://bbs.saraba1st.com/2b/thread-2103011-1-1.html"
        target="_blank"
      >
        Stage1st
      </n-a>
      或者
      <n-a href="https://tieba.baidu.com/p/8276724398" target="_blank">
        百度贴吧
      </n-a>
      上回复我。
    </n-p>

    <CommentList :post-id="route.path" />
  </MainLayout>
</template>
