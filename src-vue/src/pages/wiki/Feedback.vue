<template>
  <WikiLayout>
    <n-h1>反馈</n-h1>
    <n-p>这是给大家反馈的地方，有什么意见或者错误都可以发到这里。</n-p>
    <n-p>如果是特定页面有问题记得附上链接。</n-p>
    <n-p>
      你也可以在
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
      上找我。
    </n-p>
    <n-p>
      本项目在
      <n-a
        href="https://github.com/FishHawk/web-novel-ebook-generator"
        target="_blank"
      >
        GitHub
      </n-a>
      上开源。
    </n-p>

    <SectionComment :post-id="'/feedback'" />
  </WikiLayout>
</template>
