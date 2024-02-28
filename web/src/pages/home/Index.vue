<script lang="ts" setup>
import { ReadMoreOutlined } from '@vicons/material';
import { useThemeVars } from 'naive-ui';
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { notice } from '@/components/NoticeBoard.vue';
import { ApiUser } from '@/data/api/api_user';
import { ApiWebNovel, WebNovelOutlineDto } from '@/data/api/api_web_novel';
import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { Ok, ResultState } from '@/data/result';
import { useUserDataStore } from '@/data/stores/user_data';
import { parseUrl } from '@/data/util_web';
import bannerUrl from '@/images/banner.webp';

const userData = useUserDataStore();
const router = useRouter();
const vars = useThemeVars();

const url = ref('');
const query = (url: string) => {
  if (url.length === 0) return;
  const parseResult = parseUrl(url);
  if (parseResult !== undefined) {
    const { providerId, novelId } = parseResult;
    router.push({ path: `/novel/${providerId}/${novelId}` });
  } else {
    router.push({ path: `/novel-list`, query: { query: url } });
  }
};

const favoriteList = ref<ResultState<WebNovelOutlineDto[]>>();
const loadFavorite = async () => {
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
};
watch(
  () => userData.username,
  (username) => {
    if (username) loadFavorite();
  },
  { immediate: true }
);

const mostVisitedWeb = ref<ResultState<WebNovelOutlineDto[]>>();
const loadWeb = async () => {
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
};
loadWeb();

const latestUpdateWenku = ref<ResultState<WenkuNovelOutlineDto[]>>();
const loadWenku = async () => {
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
};
loadWenku();

const showHowToUseModal = ref(false);
const linkExample = [
  ['Kakuyomu', 'https://kakuyomu.jp/works/16817139555217983105'],
  [
    '成为小说家吧',
    'https://ncode.syosetu.com/n0833hi <br /> https://novel18.syosetu.com/n3192gh',
  ],
  ['Novelup', 'https://novelup.plus/story/206612087'],
  ['Hameln', 'https://syosetu.org/novel/297874/'],
  [
    'Pixiv系列/短篇',
    'https://www.pixiv.net/novel/series/9406879 <br/> https://www.pixiv.net/novel/show.php?id=18304868',
  ],
  ['Alphapolis', 'https://www.alphapolis.co.jp/novel/638978238/525733370'],
];

const showQQModal = ref(false);
const qqLink =
  'http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=Qa0SOMBYZoJZ4vuykz3MbPS0zbpeN0pW&authKey=q75E7fr5CIBSDhqX%2F4kuC%2B0mcPiDvj%2FSDfP%2FGZ8Rl8kDn6Z3M6XPSZ91yt4ZWonq&noverify=0&group_code=819513328';

const telegramLink = 'https://t.me/+Mphy0wV4LYZkNTI1';
const githubLink = 'https://github.com/FishHawk/auto-novel';

const notices = [
  notice('1月20日晚10点网站出现恶性bug，如果你能看到这行字，说明你没有问题。'),
  notice(
    '如果发现Sakura某段翻译得不准确，可以点击该段提交（需要登录），帮助我们改善Sakura模型。'
  ),
];
</script>

<template>
  <div
    :style="{
      background: `rgba(0, 0, 0, .15) url(${bannerUrl})`,
      'background-blend-mode': 'darken',
      width: '100%',
      'padding-top': '20px',
      'padding-bottom': '50px',
    }"
  >
    <div class="layout-content" style="max-width: 800px">
      <n-h1
        style="
          text-align: center;
          font-size: 3em;
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
          :style="{ 'background-color': vars.bodyColor }"
        />
        <n-button size="large" type="primary" @click="query(url)">
          搜索
        </n-button>
      </n-input-group>
    </div>
  </div>

  <div class="layout-content">
    <notice-board :notices="notices" style="margin-top: 24px">
      <n-flex>
        <n-button text type="primary" @click="showHowToUseModal = true">
          使用说明
        </n-button>
        /
        <n-button text type="primary" @click="showQQModal = true">
          QQ群
        </n-button>
        /
        <n-a :href="telegramLink" target="_blank">Telegram</n-a>
        /
        <n-a :href="githubLink" target="_blank">Github</n-a>
      </n-flex>
    </notice-board>

    <template v-if="userData.isLoggedIn">
      <section-header title="我的收藏">
        <router-link to="/favorite">
          <c-button label="更多" :icon="ReadMoreOutlined" />
        </router-link>
      </section-header>
      <PanelWebNovel :list-result="favoriteList" />
      <n-divider />
    </template>

    <section-header title="网络小说-最多点击">
      <router-link to="/novel-list">
        <c-button label="更多" :icon="ReadMoreOutlined" />
      </router-link>
    </section-header>
    <PanelWebNovel :list-result="mostVisitedWeb" />
    <n-divider />

    <section-header title="文库小说-最新更新">
      <router-link to="/wenku-list">
        <c-button label="更多" :icon="ReadMoreOutlined" />
      </router-link>
    </section-header>
    <PanelWenkuNovel :list-result="latestUpdateWenku" />
    <n-divider />
  </div>

  <c-modal title="使用说明" v-model:show="showHowToUseModal">
    <n-p>
      将小说链接复制到网站首页的输入框里，点击搜索，如果链接正确，将会跳转到小说页面。更高级的用法，例如生成机翻、高级搜索等，参见
      <RouterNA to="/forum/64f3d63f794cbb1321145c07">使用教程</RouterNA>
      。有什么问题和建议请在
      <RouterNA to="/forum/64f3e280794cbb1321145c09"> 反馈&建议 </RouterNA>
      集中讨论。
    </n-p>
    <n-p> 支持的小说站如下: </n-p>
    <n-p v-for="[name, link] of linkExample">
      <b>{{ name }}</b>
      <br />
      <span v-html="link" />
    </n-p>
  </c-modal>

  <c-modal title="QQ群" v-model:show="showQQModal">
    <n-p>
      交流群：
      <n-a :href="qqLink" target="_blank">819513328</n-a>
      ，验证答案是“绿色”。
      <br />
      <n-qr-code :size="150" :value="qqLink" />
    </n-p>
  </c-modal>
</template>
