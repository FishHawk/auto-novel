<script lang="ts" setup>
import { ref } from 'vue';
import { useMessage } from 'naive-ui';
import { createReusableTemplate } from '@vueuse/core';

import { YoudaoTranslator } from '@/data/translator/youdao';
import { BaiduTranslator } from '@/data/translator/baidu';

const [DefineExtensionTutorial, ReuseExtensionTutorial] =
  createReusableTemplate<{ browser: string }>();

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
      网页端中文翻译需要从你的浏览器访问翻译网站，因此需要安装扩展解决跨域的问题。
    </n-p>

    <n-ul>
      <n-li>
        支持 Chrome / Edge / Firefox 浏览器。
        国产浏览器绝大多数使用的是Chrome内核，可以参考Chrome的安装方式。
        但是有些浏览器（比如搜狗）不提供从本地文件安装插件，这种只能换浏览器了。
      </n-li>
      <n-li>
        连续翻译太多的话，会被翻译站禁一段时间。你可以等几个小时再试试，或者换台机器。
      </n-li>
      <n-li>
        在一部分机器上，Chrome/Edge即使安装了也无法使用有道翻译。由于我手边没有这样的机子，暂时无法解决。
      </n-li>
    </n-ul>

    <n-p>当前版本：v1.0.3 (2023-06-28)，安装步骤如下：</n-p>
    <DefineExtensionTutorial v-slot="{ browser }">
      <n-p>
        首先点击下面链接下载插件压缩包，解压下载的zip压缩包到文件夹，会有一个叫做extension的文件夹。
      </n-p>
      <n-p>
        <n-a href="/extension.zip" target="_blank">浏览器扩展(点此下载)</n-a>
      </n-p>
      <n-p>然后打开浏览器，进入扩展管理页面，按照下面步骤安装扩展。</n-p>
      <n-ol>
        <n-li>启用开发者模式。</n-li>
        <n-li>
          点击“加载已解压的扩展程序”，选择之前解压出的extension文件夹。
        </n-li>
        <n-li>安装成功后，会出现名为“轻小说机翻机器人”的扩展。</n-li>
      </n-ol>
      <n-p>
        如果你觉得本地存个extensions文件夹太恶心了，可以自己打包安装。
      </n-p>
      <img :src="`/${browser}.png`" style="width: 100%" />
    </DefineExtensionTutorial>

    <n-tabs type="line">
      <n-tab-pane name="chrome" tab="Chrome">
        <ReuseExtensionTutorial browser="chrome" />
      </n-tab-pane>
      <n-tab-pane name="edge" tab="Edge">
        <n-p>注意，只支持使用了Chrome内核之后的版本。</n-p>
        <ReuseExtensionTutorial browser="edge" />
      </n-tab-pane>
      <n-tab-pane name="firefox" tab="Firefox">
        <n-p>在Firefox中点击下面链接下载插件文件，即可直接安装。</n-p>
        <n-p>
          <n-a href="/extension.xpi" target="_blank">
            浏览器扩展(点此下载)
          </n-a>
        </n-p>
        <n-p>
          你也可以用其他办法下载插件文件后，在Firefox的插件页面，选择从文件安装。
        </n-p>
      </n-tab-pane>
    </n-tabs>

    <n-divider />
    <n-p>安装好扩展后可以用下面的按钮来测试能否翻译。</n-p>
    <n-p>日文：{{ textJp }}</n-p>
    <n-p>百度：{{ textBaidu }}</n-p>
    <n-p>有道：{{ textYoudao }}</n-p>
    <n-space>
      <n-button @click="testBaidu()">测试百度</n-button>
      <n-button @click="testYoudao()">测试有道</n-button>
    </n-space>

    <n-h2 prefix="bar">移动端</n-h2>
    <n-p>有一些手机浏览器可以安装插件，比如Yandex、Kiwi。</n-p>
    <n-p>想要APP？在做了在做了（指新建文件夹）</n-p>

    <n-h2 prefix="bar">GPT翻译器</n-h2>
    <n-p>
      GPT翻译插件的工作量太大，就目前来说是不现实的。
      所以我做了个GPT翻译器的程序，你可以本地运行，来生成GPT翻译。
      我也知道有GPT帐号的人很少，但我也没想好有什么好的运营方法。
      帐号本身成本1元1个，不过过程没法自动化。
      目前我会先把我自己看的几本翻了。
      如果你有什么主意欢迎分享。
    </n-p>
    <n-ul>
      <n-li>因为还在测试中，功能还不完全，暂时只支持翻译网络小说。</n-li>
      <n-li>GPT翻译器使用的是ChatGPT3，请使用OpenAI的帐号。</n-li>
      <n-li>
        GPT的反爬机制一直在更新，如果使用不了，首先下载最新的版本，还是不行再向我反馈。
      </n-li>
    </n-ul>
    <n-p> 如果你想先看看GPT翻译效果，可以试试这两本： </n-p>
    <n-ul>
      <n-li>
        <n-a href="/novel/novelup/206612087" target="_blank">黑之战记</n-a>
      </n-li>
      <n-li>
        <n-a href="/novel/hameln/232822" target="_blank">
          和风幻想的忧郁工口游戏
        </n-a>
        这一本用的是旧版本，输出语言还不稳定
      </n-li>
    </n-ul>
    <n-p>
      <n-a href="/files-extra/GPT-v0.0.2.exe" target="_blank">
        GPT翻译器-v0.0.2(点此下载)
      </n-a>
      <n-a href="/files-extra/GPT-v0.0.1.exe" target="_blank">
        GPT翻译器-v0.0.1(点此下载)
      </n-a>
    </n-p>
  </MainLayout>
</template>
