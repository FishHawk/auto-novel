<script lang="ts" setup>
import { Ref, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';

import ApiWebNovel from '../data/api/api_web_novel';
import { useAuthInfoStore } from '../data/stores/authInfo';
import { parseUrl } from '../data/provider';

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

async function loadMyFavorite(page: number, selected: number[]) {
  return ApiWebNovel.listFavorite(authInfoStore.token!!);
}
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
      margin-bottom: 40px;
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

    <template v-if="authInfoStore.token">
      <n-h2 prefix="bar">我的收藏</n-h2>
      <WebBookList :search="false" :options="[]" :loader="loadMyFavorite" />
    </template>

    <n-h2 prefix="bar">链接示例</n-h2>
    <n-ul>
      <n-li> Kakuyomu: https://kakuyomu.jp/works/16817139555217983105 </n-li>
      <n-li> 成为小说家吧: https://ncode.syosetu.com/n0833hi </n-li>
      <n-li> Novelup: https://novelup.plus/story/206612087 </n-li>
      <n-li> Hameln: https://syosetu.org/novel/297874/ </n-li>
      <n-li> Pixiv: https://www.pixiv.net/novel/series/9406879 </n-li>
      <n-li>
        Alphapolis: https://www.alphapolis.co.jp/novel/638978238/525733370
      </n-li>
    </n-ul>
  </MainLayout>
</template>
