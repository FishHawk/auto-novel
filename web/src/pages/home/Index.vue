<script lang="ts" setup>
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { ApiUser } from '@/data/api/api_user';
import { ApiWebNovel, WebNovelOutlineDto } from '@/data/api/api_web_novel';
import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { Ok, ResultState } from '@/data/result';
import { useUserDataStore } from '@/data/stores/user_data';
import { useIsDesktop } from '@/data/util';
import { parseUrl } from '@/data/util_web';
import bannerUrl from '@/images/banner.webp';
import qqUrl from '@/images/qq.png';

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
  const result = await ApiUser.listFavoredWebNovel('default', {
    page: 0,
    pageSize: 8,
    sort: 'update',
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
  const result = await ApiWebNovel.listNovel({
    page: 0,
    pageSize: 8,
    sort: 1,
    level: 1,
  });
  if (result.ok) {
    mostVisitedWeb.value = Ok(result.value.items);
  } else {
    mostVisitedWeb.value = result;
  }
}
loadWeb();

const latestUpdateWenku = ref<ResultState<WenkuNovelOutlineDto[]>>();
async function loadWenku() {
  const result = await ApiWenkuNovel.listNovel({
    page: 0,
    pageSize: 12,
    level: 1,
  });
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

    <n-space :wrap="false" align="center" style="margin-top: 8px">
      <img v-if="isDesktop" :src="qqUrl" width="120" />

      <n-ul>
        <n-li>GPT修好了。</n-li>
        <n-li>
          如果发现Sakura某段翻译得不准确，可以点击该段提交（需要登录），帮助我们改善Sakura模型。
        </n-li>
        <n-li>今年年底会屏蔽日韩的IP，请日韩的朋友们做好准备。</n-li>
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
        <RouterNA to="/favorite">更多</RouterNA>
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

    <card-modal v-model:show="showLinkExampleModal">
      <n-list>
        <n-list-item v-for="[name, link] of linkExample">
          <n-thing>
            <template #description>
              <b>{{ name }}</b>
              <br />
              {{ link }}
            </template>
          </n-thing>
        </n-list-item>
      </n-list>
    </card-modal>
  </MainLayout>
</template>
