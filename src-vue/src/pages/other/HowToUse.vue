<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useMessage } from 'naive-ui';
import { createReusableTemplate } from '@vueuse/core';

import { useSettingStore } from '@/data/stores/setting';
import { createTranslator } from '@/data/translator/translator';

const [DefineExtensionTutorial, ReuseExtensionTutorial] =
  createReusableTemplate<{ browser: string }>();

const message = useMessage();

const textJp = 'あたしの悪徳領主様!!　～俺は星間国家の悪徳領主！　外伝～';
const textBaidu = ref();
const textYoudao = ref();
const textGpt = ref('');

const setting = useSettingStore();
const gptAccessToken = ref();
const gptAccessTokenOptions = computed(() => {
  return setting.openAiAccessTokens.map((t) => {
    return { label: t, value: t };
  });
});

async function testBaidu() {
  try {
    const t = await createTranslator('baidu', {});
    const r = await t.translate([textJp]);
    textBaidu.value = r[0];
  } catch (e: any) {
    message.error(e.message);
  }
}

async function testYoudao() {
  try {
    const t = await createTranslator('youdao', {});
    const r = await t.translate([textJp]);
    textYoudao.value = r[0];
  } catch (e: any) {
    message.error(e.message);
  }
}

async function testGpt() {
  try {
    const t = await createTranslator('gpt', {
      accessToken: gptAccessToken.value,
    });
    const r = await t.translate([textJp]);
    textGpt.value = r[0];
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
      支持 Chrome / Edge / Firefox 浏览器。
      国产浏览器绝大多数使用的是Chrome内核，可以参考Chrome的安装方式。
      但是有些浏览器（比如搜狗）不提供从本地文件安装插件，这种只能换浏览器了。
    </n-p>

    <n-p>有道/百度说明：</n-p>
    <n-ul>
      <n-li>插件安装了就好了，直接在小说页面点更新按钮就能开始翻译。</n-li>
      <n-li>
        百度/有道连续翻译太多的话，会被翻译站禁一段时间。你可以等几个小时再试试，或者换台机器。
      </n-li>
      <n-li>
        在一部分机器上，Chrome/Edge即使安装了也无法使用有道翻译。由于我手边没有这样的机子，暂时无法解决。
      </n-li>
    </n-ul>
    <n-p>GPT说明:</n-p>
    <n-ul>
      <n-li>在启动GPT翻译之前，你得先做一些准备操作：</n-li>
      <n-ul>
        <n-li>打开要启动翻译任务的网页。</n-li>
        <n-li>
          右键插件，点击“启动调试器以支持GPT”。此时浏览器会提示调试器打开。
          注意，虽然浏览器的提示在任何一个标签页都能看到，但时调试器只有在当前页面才有用，所以必须要在要翻译的页面启动调试器。
        </n-li>
        <n-li>
          在更新按钮上面的文本框里输入你帐号的GPT access token。具体如下：
        </n-li>
        <n-ul>
          <n-li>
            首先登录到
            <n-a href="https://chat.openai.com/" target="_blank">OpenAi</n-a>。
          </n-li>
          <n-li>
            然后打开
            <n-a href="https://chat.openai.com/api/auth/session" target="_blank"
              >Token</n-a
            >
            ，将这一页的内容全部复制进去，可以自动识别token。
          </n-li>
        </n-ul>
      </n-ul>
      <n-li>
        GPT翻译非常慢，建议多注册几个号，注册方法参考
        <n-a
          href="https://github.com/xiaoming2028/FreePAC/wiki/ChatGPT%E6%B3%A8%E5%86%8C%E6%95%99%E7%A8%8B%EF%BC%88%E5%AE%8C%E6%95%B4%E6%8C%87%E5%8D%97%EF%BC%89"
          target="_blank"
        >
          ChatGPT注册教程
        </n-a>
        成本为1元1个，但是最少要充2美元。记得活用高级功能控制翻译范围。
      </n-li>
      <n-li>一个GPT帐号不要同时启动多个翻译任务。</n-li>
      <n-li>GPT有额度限制，到达额度限制后请不要反复尝试。</n-li>
      <n-li><b>火狐暂不支持，请等待更新</b></n-li>
    </n-ul>

    <n-p>当前版本：v1.0.4 (2023-07-17)，安装步骤如下：</n-p>
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

    <n-auto-complete
      v-model:value="gptAccessToken"
      :options="gptAccessTokenOptions"
      placeholder="请输入GPT的Access Token"
      :get-show="() => true"
    />
    <n-p>日文：{{ textJp }}</n-p>
    <n-p>百度：{{ textBaidu }}</n-p>
    <n-p>有道：{{ textYoudao }}</n-p>
    <n-p>GPT：{{ textGpt }}</n-p>
    <n-space>
      <n-button @click="testBaidu()">测试百度</n-button>
      <n-button @click="testYoudao()">测试有道</n-button>
      <n-button @click="testGpt()">测试GPT</n-button>
    </n-space>

    <n-h2 prefix="bar">移动端</n-h2>
    <n-p>有一些手机浏览器可以安装插件，比如Yandex、Kiwi。</n-p>
    <n-p>想要APP？在做了在做了（指新建文件夹）</n-p>

    <n-h2 prefix="bar"><del>GPT翻译器</del>（弃用，用新版插件吧）</n-h2>
    <n-p>
      <del>GPT翻译插件的工作量太大，就目前来说是不现实的。</del>（现在现实了）
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
    <n-ul>
      <n-li>
        <n-a href="/files-extra/GPT-v0.0.2.exe" target="_blank">
          GPT翻译器-v0.0.2(点此下载)
        </n-a>
      </n-li>
      <n-li>
        <n-a href="/files-extra/GPT-v0.0.1.exe" target="_blank">
          GPT翻译器-v0.0.1(点此下载)
        </n-a>
      </n-li>
    </n-ul>
  </MainLayout>
</template>
