<script lang="ts" setup>
import { onMounted, Ref, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';

import { getHistories, SearchHistory } from '../data/history';
import { parseUrl } from '../data/provider';

const router = useRouter();
const message = useMessage();

const url: Ref<string> = ref('');
const historiesRef: Ref<SearchHistory[]> = ref([]);

function getNovelUrl(url: string) {
  if (url.length === 0) {
    return;
  }
  const parseResult = parseUrl(url);
  if (parseResult === undefined) {
    message.error('无法解析网址，可能是因为格式错误或者不支持');
    return;
  }
  const providerId = parseResult.providerId;
  const bookId = parseResult.bookId;
  return `/novel/${providerId}/${bookId}`;
}

function query(url: string) {
  const novelUrl = getNovelUrl(url);
  if (!novelUrl) return;
  router.push({ path: novelUrl });
}

function navToList() {
  router.push({ path: `/list` });
}

onMounted(() => {
  historiesRef.value = getHistories();
});
</script>

<template>
  <div
    style="
      background: url('https://images.unsplash.com/photo-1521587760476-6c12a4b040da?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1170&q=80')
      width: 100%;
      padding-top: 100px;
      padding-bottom: 100px;
    "
  >
    <div class="content" style="margin-top: 50px">
      <n-h1
        style="
          text-align: center;
          font-size: 3.5em;
          color: white;
          filter: drop-shadow(0.05em 0.05em black);
        "
      >
        日本网文机翻机器人
      </n-h1>
      <n-input
        v-model:value="url"
        size="large"
        round
        placeholder="请输入小说链接..."
        @keyup.enter="query(url)"
      >
      </n-input>
      <n-space justify="space-around" size="large" style="margin-top: 20px">
        <n-button type="primary" @click="query(url)">搜索</n-button>
        <n-button type="primary" @click="navToList">小说列表</n-button>
      </n-space>
    </div>
  </div>

  <div class="content" style="margin-top: 50px">
    <div v-if="historiesRef.length > 0">
      <n-h2 prefix="bar" align-text>搜索历史</n-h2>
      <ul>
        <li v-for="history in historiesRef">
          <n-a :href="getNovelUrl(history.url)" target="_blank">{{
            history.title
          }}</n-a>
        </li>
      </ul>
    </div>

    <n-h2 prefix="bar" align-text>链接示例</n-h2>
    <n-ul>
      <n-li> Kakuyomu: https://kakuyomu.jp/works/16817139555217983105 </n-li>
      <n-li> 成为小说家吧: https://ncode.syosetu.com/n0833hi </n-li>
      <n-li> Novelup: https://novelup.plus/story/206612087 </n-li>
      <n-li> Hameln: https://syosetu.org/novel/297874/ </n-li>
      <n-li> Pixiv: https://www.pixiv.net/novel/series/9406879 </n-li>
    </n-ul>

    <n-h2 prefix="bar" align-text>如何使用中文翻译</n-h2>
    <n-p>
      中文翻译需要从你的浏览器访问翻译网站，因此需要安装插件解决跨域的问题。
    </n-p>
    <n-p>
      对于 Chrome/Edge/Firefox 浏览器，你需要安装插件 CORS Unblock。下载链接：
      <n-a
        href="https://chrome.google.com/webstore/detail/cors-unblock/lfhmikememgdcahcdlaciloancbhjino/"
        target="_blank"
      >
        Chrome
      </n-a>
      /
      <n-a
        href="https://microsoftedge.microsoft.com/addons/detail/cors-unblock/hkjklmhkbkdhlgnnfbbcihcajofmjgbh"
        target="_blank"
      >
        Edge
      </n-a>
      /
      <n-a
        href="https://addons.mozilla.org/en-US/firefox/addon/cors-unblock/"
        target="_blank"
      >
        Firefox
      </n-a>
      。如果无法访问 Chrome 的插件页面，你也可以从
      <n-a href="/CORS-Unblock.crx" target="_blank">这里</n-a>
      直接下载插件文件。 为了安全，建议只在本站点启用该插件。
      安装以后，具体配置如下图所示。
    </n-p>
    <img
      src="/extension_options.png"
      alt="extension options"
      style="max-width: 100%"
    />
    <n-p>
      对于 Safari 浏览器，只能各凭本事了。 尽管 Safari
      可以修改启动选项来关闭跨域检查，但我不知道有没有什么办法删掉 referer 和
      origin 头。
    </n-p>

    <n-h2 prefix="bar" align-text>联系我</n-h2>
    如果发现问题或者有什么建议，欢迎到
    <n-a
      href="https://github.com/FishHawk/web-novel-ebook-generator"
      target="_blank"
    >
      GitHub
    </n-a>
    上提issue，也可以在
    <n-a
      href="https://bbs.saraba1st.com/2b/thread-2103011-1-1.html"
      target="_blank"
    >
      Stage1st
    </n-a>
    或者
    <n-a
      href="https://tieba.baidu.com/p/8276724398"
      target="_blank"
    >
      百度贴吧
    </n-a>
    上回复我。
  </div>
</template>
