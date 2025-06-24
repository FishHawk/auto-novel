<script lang="ts" setup>
import avaterUrl from '@/image/avater.jpg';

const guides = [
  `
# 一级标题
## 二级标题
### 三级标题`,

  `
**粗体**
*斜体*
~~删除线~~
[链接](https://www.esjzone.cc/)`,

  `
- 无序列表
- 无序列表
  - 加空格可以缩进
  - 加空格可以缩进
- 无序列表`,

  `
1. 有序列表
1. 有序列表
1. 有序列表`,

  `
下面是分隔线，和文本要用空行隔开

---`,
  `
| 左对齐 | 居中 | 右对齐 |
| :- | :-: | -: |
| 文本 | 文本 | 文本 |
| 文本 | 文本 | 文本 |`,

  `
![](${avaterUrl})`,
];
</script>

<template>
  <c-modal title="格式教程">
    <n-p>评论不支持标题、表格、图片等高级功能。</n-p>
    <n-table :bordered="false" style="table-layout: fixed; width: 100%">
      <thead>
        <tr>
          <th><b>语法</b></th>
          <th><b>预览</b></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="guide in guides">
          <td style="white-space: pre-wrap">
            {{ guide.trimStart() }}
          </td>
          <td>
            <MarkdownView mode="article" :source="guide" />
          </td>
        </tr>
      </tbody>
    </n-table>
  </c-modal>
</template>
