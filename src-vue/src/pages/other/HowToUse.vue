<script lang="ts" setup>
import { ref } from 'vue';
import { useMessage } from 'naive-ui';

import { YoudaoTranslator } from '@/data/translator/youdao';
import { BaiduTranslator } from '@/data/translator/baidu';

const message = useMessage();

const textJp = 'あたしの悪徳領主様!!　～俺は星間国家の悪徳領主！　外伝～';
const textBaidu = ref();
const textYoudao = ref();

async function testBaidu() {
  try {
    const t = await BaiduTranslator.create();
    const r = await t.translate([textJp]);
    textBaidu.value = r[0];
  } catch (e: any) {
    message.error(e.message);
  }
}

async function testYoudao() {
  try {
    const t = await YoudaoTranslator.create();
    const r = await t.translate([textJp]);
    textYoudao.value = r[0];
  } catch (e: any) {
    message.error(e.message);
  }
}
</script>

<template>
  <MainLayout>
    <n-h1>使用说明</n-h1>

    <n-h2 prefix="bar">网页端</n-h2>
    <n-p>
      网页端中文翻译需要从你的浏览器访问翻译网站，因此需要安装插件解决跨域的问题。
    </n-p>
    <n-p>
      此外，连续翻译太多的话，会被翻译站禁一段时间，等几个小时就好了，也可以换台机子。
    </n-p>
    <n-table :bordered="false" :single-line="false">
      <thead>
        <tr>
          <th>浏览器</th>
          <th>说明</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td nowrap="nowrap">Chrome</td>
          <td><n-a href="/extension.zip" target="_blank">浏览器插件</n-a></td>
        </tr>
        <tr>
          <td nowrap="nowrap">Edge</td>
          <td>
            <n-a href="/extension.zip" target="_blank">浏览器插件</n-a>
            （只支持使用了Chrome内核之后的Edge版本）
          </td>
        </tr>
        <tr>
          <td nowrap="nowrap">Firefox</td>
          <td>优先级不高，你可以让我知道你要这个，来提升优先级</td>
        </tr>
        <tr>
          <td nowrap="nowrap">Safari</td>
          <td>各凭本事</td>
        </tr>
      </tbody>
    </n-table>

    <n-h4 prefix="bar">Chrome/Edge插件安装步骤</n-h4>
    <n-ul>
      <n-li>下载插件zip，解压到文件夹。</n-li>
      <n-li>进入插件页面，启用开发者模式。</n-li>
      <n-li>点击“加载已解压的扩展程序”，选择解压的文件夹。</n-li>
    </n-ul>

    <n-h4 prefix="bar">测试翻译</n-h4>
    <n-p>安装好插件后可以用下面的按钮来测试能否翻译。</n-p>
    <n-p>日文：{{ textJp }}</n-p>
    <n-p>百度：{{ textBaidu }}</n-p>
    <n-p>有道：{{ textYoudao }}</n-p>
    <n-space>
      <n-button @click="testBaidu()">测试百度</n-button>
      <n-button @click="testYoudao()">测试有道</n-button>
    </n-space>

    <n-h2 prefix="bar">移动端</n-h2>
    <n-table :bordered="false" :single-line="false">
      <thead>
        <tr>
          <th>手机系统</th>
          <th>说明</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td nowrap="nowrap">Android</td>
          <td>在做了在做了</td>
        </tr>
        <tr>
          <td nowrap="nowrap">IOS</td>
          <td>没戏，因为给苹果写App要先给它交钱</td>
        </tr>
      </tbody>
    </n-table>
  </MainLayout>
</template>
