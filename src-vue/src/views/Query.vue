<script lang="ts" setup>
import { onMounted, Ref, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';

import ApiWebNovel, {
  BookListItemDto,
  BookListPageDto,
} from '../data/api/api_web_novel';
import { useAuthInfoStore } from '../data/stores/authInfo';
import { parseUrl } from '../data/provider';
import { Ok, Result, ResultState } from '../data/api/result';

const authInfoStore = useAuthInfoStore();

const router = useRouter();
const message = useMessage();

const url: Ref<string> = ref('');

function query(url: string) {
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
  const novelUrl = `/novel/${providerId}/${bookId}`;
  router.push({ path: novelUrl });
}

const favoriteList = ref<ResultState<BookListItemDto[]>>();
async function loadPage() {
  const result = await ApiWebNovel.listFavorite(authInfoStore.token!!);
  if (result.ok) {
    favoriteList.value = Ok(result.value.items.slice(0, 8));
  } else {
    favoriteList.value = result;
  }
}
onMounted(loadPage);

const latestUpdate = ref<ResultState<BookListItemDto[]>>();
async function loadLatestUpdate() {
  const result = await ApiWebNovel.list(0, '', '');
  if (result.ok) {
    favoriteList.value = Ok(result.value.items.slice(0, 8));
  } else {
    favoriteList.value = result;
  }
}
onMounted(loadLatestUpdate);
</script>

<template>
  <MainLayout>
    <template v-slot:full-width>
      <div
        style="
      background: url('https://images.unsplash.com/photo-1521587760476-6c12a4b040da?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1170&q=80')
      width: 100%;
      padding-top: 60px;
      padding-bottom: 60px;
      margin-bottom: 20px;
    "
      >
        <div class="container" style="max-width: 800px">
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
          <n-input-group>
            <n-input
              v-model:value="url"
              size="large"
              placeholder="请输入小说链接..."
              @keyup.enter="query(url)"
            />
            <n-button size="large" type="primary" @click="query(url)">
              搜索
            </n-button>
          </n-input-group>
        </div>
      </div>
    </template>

    <div style="display: flex">
      <div style="flex: 1; margin-right: 16px">
        <n-h2 prefix="bar">公告</n-h2>
        <n-p>
          2023年4月10日，重写了插件以支持有道翻译，参见
          <n-a href="/how-to-use" target="_blank">使用说明</n-a>
          。 章节设置里可以切换百度和有道。 旧插件仍然能用，但只能用百度翻译。
          大更新免不了有bug，欢迎反馈。
        </n-p>
        <n-p>
          Alphapolis和Pixiv是我用自己的cookie垫进去的，如果加载不了就是我cookie过期了，请提醒我更新。
        </n-p>
      </div>
      <div style="flex: 1">
        <n-h2 prefix="bar">链接示例</n-h2>
        <n-ul>
          <n-li>
            Kakuyomu: https://kakuyomu.jp/works/16817139555217983105
          </n-li>
          <n-li> 成为小说家吧: https://ncode.syosetu.com/n0833hi </n-li>
          <n-li> Novelup: https://novelup.plus/story/206612087 </n-li>
          <n-li> Hameln: https://syosetu.org/novel/297874/ </n-li>
          <n-li> Pixiv: https://www.pixiv.net/novel/series/9406879 </n-li>
          <n-li>
            Alphapolis: https://www.alphapolis.co.jp/novel/638978238/525733370
          </n-li>
          <n-li>
            Novelism: https://novelism.jp/novel/2m0xulekSsCxfixwam8d7g
          </n-li>
        </n-ul>
      </div>
    </div>
    <n-divider />

    <template v-if="authInfoStore.token">
      <n-space align="center" justify="space-between">
        <n-h2 prefix="bar">我的收藏</n-h2>
        <n-a>更多</n-a>
      </n-space>
      <WebBookPanel :list="favoriteList" />
      <n-divider />
    </template>

    <n-space align="center" justify="space-between">
      <n-h2 prefix="bar">最新更新</n-h2>
      <n-a href="/novel-list">更多</n-a>
    </n-space>
    <WebBookPanel :list="favoriteList" />
  </MainLayout>
</template>
