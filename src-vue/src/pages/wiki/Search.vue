<template>
  <WikiLayout>
    <n-h1>如何使用搜索</n-h1>

    <n-h2 prefix="bar">例子</n-h2>

    基本用法：
    <n-ul>
      <n-li>
        <n-text code>勇者 魔王</n-text>
        ：搜索结果包含 <n-text code>勇者</n-text> <b>且</b> 包含
        <n-text code>魔王</n-text> 的小说。
      </n-li>
      <n-li>
        <n-text code>勇者 | 魔王</n-text>
        ：搜索结果包含 <n-text code>勇者</n-text> <b>或</b> 包含
        <n-text code>魔王</n-text> 的小说。
      </n-li>
      <n-li>
        <n-text code>勇者 -魔王</n-text>
        ：搜索结果包含 <n-text code>勇者</n-text> <b>且不</b> 包含
        <n-text code>魔王</n-text> 的小说。
      </n-li>
      <n-li>
        <n-text code>勇者 | -魔王</n-text>
        ：搜索结果包含 <n-text code>勇者</n-text> <b>或不</b> 包含
        <n-text code>魔王</n-text> 的小说。
      </n-li>
    </n-ul>

    精准匹配：
    <n-ul>
      <n-li>
        <n-text code>咒术师不能成为勇者</n-text>
        ：搜索结果既有只匹配 <n-text code>术师</n-text> 的，也有只匹配
        <n-text code>勇者</n-text> 的。
      </n-li>
      <n-li>
        <n-text code>"咒术师不能成为勇者"</n-text>
        ：搜索结果只包含严格包含这9个字的小说。
      </n-li>
    </n-ul>

    标签搜索：
    <n-ul>
      <n-li> <n-text code>恋愛$</n-text> ：搜索包含恋爱标签的小说。 </n-li>
      <n-li>
        <n-text code>-恋愛$</n-text>
        ：搜索不包含恋爱标签的小说，但是标题可能包含恋爱。
      </n-li>
    </n-ul>

    <n-h2 prefix="bar">操作符</n-h2>
    <n-p>搜索词按照空格分割，默认搜索词之间是“与”的关系。</n-p>
    <n-table :bordered="false" :single-line="false">
      <thead>
        <tr>
          <th>符号</th>
          <th>效果</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>|</td>
          <td>连接搜索词，表示“或”的关系。</td>
        </tr>
        <tr>
          <td>-</td>
          <td>放在搜索词开头，会阻止匹配该词的搜索结果。</td>
        </tr>
        <tr>
          <td>""</td>
          <td>
            精确字符串搜索。引号内的内容将被视作一个词，不会尝试部分匹配。
          </td>
        </tr>
        <tr>
          <td>()</td>
          <td>用来控制逻辑运算的优先级。</td>
        </tr>
        <tr>
          <td>$</td>
          <td>
            放在搜索词结尾，表示这个搜索词用来严格匹配标签。可以和-匹配来反向过滤标签，不参与其它操作符的运算。
          </td>
        </tr>
      </tbody>
    </n-table>
  </WikiLayout>
</template>
