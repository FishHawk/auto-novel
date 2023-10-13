<script lang="ts" setup>
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import qqUrl from '@/images/qq.png';
import bannerUrl from '@/images/banner.webp';
import { ApiWebNovel, WebNovelOutlineDto } from '@/data/api/api_web_novel';
import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { parseUrl } from '@/data/provider';
import { Ok, ResultState } from '@/data/api/result';
import { useUserDataStore } from '@/data/stores/userData';
import { useIsDesktop } from '@/data/util';

const isDesktop = useIsDesktop(900);
const userData = useUserDataStore();
const router = useRouter();

const url = ref('');
function query(url: string) {
  if (url.length === 0) return;
  const parseResult = parseUrl(url);
  if (parseResult !== undefined) {
    const { providerId, novelId } = parseResult;
    router.push({ path: `/novel/${providerId}/${novelId}` });
  } else {
    router.push({ path: `/novel-list`, query: { query: url } });
  }
}

const favoriteList = ref<ResultState<WebNovelOutlineDto[]>>();
async function loadFavorite() {
  const result = await ApiWebNovel.listFavored({
    page: 0,
    pageSize: 8,
  });
  if (result.ok) {
    favoriteList.value = Ok(result.value.items);
  } else {
    favoriteList.value = result;
  }
}
watch(
  userData,
  (userData) => {
    if (userData.isLoggedIn) loadFavorite();
  },
  { immediate: true }
);

const mostVisitedWeb = ref<ResultState<WebNovelOutlineDto[]>>();
async function loadWeb() {
  const result = await ApiWebNovel.list({ page: 0, pageSize: 8, sort: 1 });
  if (result.ok) {
    mostVisitedWeb.value = Ok(result.value.items);
  } else {
    mostVisitedWeb.value = result;
  }
}
loadWeb();

const latestUpdateWenku = ref<ResultState<WenkuNovelOutlineDto[]>>();
async function loadWenku() {
  const result = await ApiWenkuNovel.list({ page: 0, pageSize: 12 });
  if (result.ok) {
    latestUpdateWenku.value = Ok(result.value.items.slice(0, 12));
  } else {
    latestUpdateWenku.value = result;
  }
}
loadWenku();

const showLinkExampleModal = ref(false);
const linkExample = [
  ['Kakuyomu', 'https://kakuyomu.jp/works/16817139555217983105'],
  ['成为小说家吧', 'https://ncode.syosetu.com/n0833hi'],
  ['Novelup', 'https://novelup.plus/story/206612087'],
  ['Hameln', 'https://syosetu.org/novel/297874/'],
  ['Pixiv系列', 'https://www.pixiv.net/novel/series/9406879'],
  ['Pixiv短篇', 'https://www.pixiv.net/novel/show.php?id=18304868'],
  ['Alphapolis', 'https://www.alphapolis.co.jp/novel/638978238/525733370'],
  ['Novelism', 'https://novelism.jp/novel/2m0xulekSsCxfixwam8d7g'],
];
</script>

<template>
  <MainLayout>
    <template v-slot:full-width>
      <div
        :style="{
          background: `rgba(0, 0, 0, .15) url(${bannerUrl})`,
          'background-blend-mode': 'darken',
          width: '100%',
          'padding-top': '60px',
          'padding-bottom': '104px',
        }"
      >
        <div class="container" style="max-width: 800px">
          <n-h1
            style="
              text-align: center;
              font-size: 3.2em;
              color: white;
              filter: drop-shadow(0.05em 0.05em black);
            "
          >
            轻小说机翻机器人
          </n-h1>
          <n-input-group>
            <n-input
              v-model:value="url"
              size="large"
              placeholder="请输入小说链接，或者输入标题搜索本站缓存..."
              :input-props="{ spellcheck: false }"
              @keyup.enter="query(url)"
            />
            <n-button size="large" type="primary" @click="query(url)">
              搜索
            </n-button>
          </n-input-group>
        </div>
      </div>
    </template>

    <n-space
      :wrap="false"
      align="center"
      style="max-width: 650px; margin-top: 8px"
    >
      <img v-if="isDesktop" :src="qqUrl" width="120" />

      <n-ul>
        <n-li>
          <b style="color: red">
            请正常使用文库小说功能，不要把生成的翻译文件上传了再翻译一遍。有特殊需求请在反馈里找我，或者在私人缓存区里上传。不然我得一本一本删，很累的。
          </b>
        </n-li>
        <n-li>
          <b> 增加了GPT分段本地缓存。 </b>
        </n-li>
        <n-li>
          <b>使用说明</b>
          ：将想要翻译的小说链接复制到网站首页的输入框里，点击搜索，如果链接正确，将会跳转到小说页面。
          <n-ul>
            <n-li>
              支持的小说站参考
              <n-text type="success" @click="showLinkExampleModal = true">
                小说链接示例
              </n-text>
              。
            </n-li>
            <n-li>
              想自己生成翻译参考
              <RouterNA to="/forum/64f3d63f794cbb1321145c07">插件教程</RouterNA>
              。
            </n-li>
            <n-li>
              有什么问题和建议请在
              <RouterNA to="/forum/64f3e280794cbb1321145c09">
                反馈&更新日志
              </RouterNA>
              集中讨论。
            </n-li>
          </n-ul>
        </n-li>
        <n-li>交流群：819513328，验证答案是“绿色”。</n-li>
      </n-ul>
    </n-space>

    <template v-if="userData.isLoggedIn">
      <SectionHeader title="我的收藏">
        <RouterNA to="/favorite-list">更多</RouterNA>
      </SectionHeader>
      <PanelWebNovel :list-result="favoriteList" />
      <n-divider />
    </template>

    <SectionHeader title="最多点击-网络小说">
      <RouterNA to="/novel-list">更多</RouterNA>
    </SectionHeader>
    <PanelWebNovel :list-result="mostVisitedWeb" />
    <n-divider />

    <SectionHeader title="最新更新-文库小说" style="margin-bottom: 20px">
      <RouterNA to="/wenku-list">更多</RouterNA>
    </SectionHeader>
    <PanelWenkuNovel :list-result="latestUpdateWenku" />
    <n-divider />

    <n-modal v-model:show="showLinkExampleModal">
      <n-card
        style="width: min(600px, calc(100% - 16px))"
        :bordered="false"
        size="large"
        role="dialog"
        aria-modal="true"
      >
        <n-scrollbar trigger="none" style="max-height: 400px">
          <n-table :bordered="false">
            <tr v-for="[name, link] of linkExample">
              <td style="white-space: nowrap">
                <b>{{ name }}</b>
              </td>
              <td>{{ link }}</td>
            </tr>
          </n-table>
        </n-scrollbar>
      </n-card>
    </n-modal>
  </MainLayout>
</template>
