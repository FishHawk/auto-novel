<script lang="ts" setup>
import { BookFilled, EditNoteFilled, SortFilled } from '@vicons/material';
import { createReusableTemplate } from '@vueuse/core';
import { NA, NText, useThemeVars } from 'naive-ui';
import { ref, shallowRef } from 'vue';
import { useRoute } from 'vue-router';

import {
  ApiWebNovel,
  WebNovelDto,
  WebNovelTocItemDto,
} from '@/data/api/api_web_novel';
import { Result, mapOk } from '@/data/result';
import { useSettingStore } from '@/data/stores/setting';
import { useIsDesktop } from '@/data/util';
import { buildWebNovelUrl, tryTranslateKeyword } from '@/data/util_web';

type ReadableTocItem = WebNovelTocItemDto & {
  index: number;
  order?: number;
};

const [DefineTag, ReuseTag] = createReusableTemplate<{
  tag: string;
  attention: boolean;
}>();

const [DefineTocItemDesktop, ReuseTocItemDesktop] = createReusableTemplate<{
  item: ReadableTocItem;
}>();

const [DefineTocItemMobile, ReuseTocItemMobile] = createReusableTemplate<{
  item: ReadableTocItem;
}>();

const setting = useSettingStore();
const isDesktop = useIsDesktop(600);
const vars = useThemeVars();

const route = useRoute();
const providerId = route.params.providerId as string;
const novelId = route.params.novelId as string;

const novelResult = ref<Result<Omit<WebNovelDto, 'toc'>>>();
const toc = shallowRef<ReadableTocItem[]>([]);
const lastReadChapter = shallowRef<ReadableTocItem>();

const getNovel = async () => {
  novelResult.value = undefined;
  const result = await ApiWebNovel.getNovel(providerId, novelId);
  const newResult = mapOk(result, (novel) => {
    const novelToc = novel.toc as ReadableTocItem[];
    let order = 0;
    let index = 0;
    for (const it of novelToc) {
      it.index = index;
      it.order = it.chapterId ? order : undefined;
      if (it.chapterId) order += 1;
      index += 1;
    }
    toc.value = novelToc;

    if (novel.lastReadChapterId) {
      lastReadChapter.value = novelToc.find(
        (it) => it.chapterId === novel.lastReadChapterId
      );
    }

    novel.toc = [];
    return novel;
  });
  novelResult.value = newResult;
  if (result.ok) {
    document.title = result.value.titleJp;
  }
};
getNovel();
</script>

<template>
  <DefineTag v-slot="{ tag, attention }">
    <router-link
      :to="`/novel-list?query=${tag}\$`"
      style="color: rgb(51, 54, 57)"
    >
      <n-tag :bordered="false" size="small">
        <template v-if="attention">
          <b>{{ tag }}</b>
        </template>
        <template v-else>
          {{ tryTranslateKeyword(tag) }}
        </template>
      </n-tag>
    </router-link>
  </DefineTag>

  <DefineTocItemDesktop v-slot="{ item }">
    <component
      :is="item.chapterId ? NA : NText"
      :href="`/novel/${providerId}/${novelId}/${item.chapterId}`"
      class="toc"
      style="width: 100%; display: flex; padding: 6px"
    >
      <span style="flex: 1 1 0">{{ item.titleJp }}</span>
      <n-text depth="3" style="flex: 1 1 0">{{ item.titleZh }}</n-text>
      <n-text depth="3" style="width: 170px; text-align: right">
        <template v-if="item.order !== undefined">
          <n-time
            v-if="item.createAt"
            :time="item.createAt * 1000"
            format="yyyy-MM-dd HH:mm"
          />
          [{{ item.order }}]
        </template>
      </n-text>
    </component>
  </DefineTocItemDesktop>

  <DefineTocItemMobile v-slot="{ item }">
    <component
      :is="item.chapterId ? NA : NText"
      :href="`/novel/${providerId}/${novelId}/${item.chapterId}`"
      class="toc"
      style="width: 100%; display: block; padding: 6px"
    >
      {{ item.titleJp }}
      <br />
      <n-text depth="3">
        {{ item.titleZh }}
        <template v-if="item.order !== undefined">
          <br />
          [{{ item.order }}]
          <n-time
            v-if="item.createAt"
            :time="item.createAt * 1000"
            format="yyyy-MM-dd HH:mm"
          />
        </template>
      </n-text>
    </component>
  </DefineTocItemMobile>

  <div class="layout-content">
    <ResultView
      :result="novelResult"
      :showEmpty="() => false"
      v-slot="{ value: novel }"
    >
      <n-h1 prefix="bar" style="font-size: 22px">
        <n-a :href="buildWebNovelUrl(providerId, novelId)">{{
          novel.titleJp
        }}</n-a>
        <br />
        <n-text depth="3">{{ novel.titleZh }}</n-text>
      </n-h1>

      <n-p v-if="novel.authors.length > 0">
        作者：
        <template v-for="author in novel.authors">
          <n-a :href="author.link">{{ author.name }}</n-a>
        </template>
      </n-p>

      <n-space>
        <RouterNA :to="`/novel-edit/${providerId}/${novelId}`">
          <n-button>
            <template #icon>
              <n-icon :component="EditNoteFilled" />
            </template>
            编辑
          </n-button>
        </RouterNA>

        <favorite-button
          v-model:favored="novel.favored"
          :favored-list="novel.favoredList"
          :novel="{ type: 'web', providerId, novelId }"
        />

        <router-link v-if="novel.wenkuId" :to="`/wenku/${novel.wenkuId}`">
          <n-button>
            <template #icon>
              <n-icon :component="BookFilled" />
            </template>
            文库
          </n-button>
        </router-link>
      </n-space>

      <n-p>{{ novel.type }} / 浏览次数:{{ novel.visited }}</n-p>

      <n-p style="word-break: break-all">
        {{ novel.introductionJp }}
      </n-p>
      <n-p
        v-if="novel.introductionZh !== undefined"
        style="word-break: break-all"
      >
        {{ novel.introductionZh }}
      </n-p>

      <n-space :size="[4, 4]">
        <ReuseTag
          v-for="attention of novel.attentions.sort()"
          :tag="attention"
          :attention="true"
        />
        <ReuseTag
          v-for="keyword of novel.keywords"
          :tag="keyword"
          :attention="false"
        />
      </n-space>

      <section>
        <SectionHeader title="翻译" />
        <WebTranslate
          :provider-id="providerId"
          :novel-id="novelId"
          :title-jp="novel.titleJp"
          :title-zh="novel.titleZh"
          :total="toc.filter((it: WebNovelTocItemDto) => it.chapterId).length"
          v-model:jp="novel.jp"
          v-model:baidu="novel.baidu"
          v-model:youdao="novel.youdao"
          v-model:gpt="novel.gpt"
          :sakura="novel.sakura"
          :glossary="novel.glossary"
        />
      </section>

      <section>
        <SectionHeader title="目录">
          <n-button @click="setting.tocSortReverse = !setting.tocSortReverse">
            <template #icon>
              <n-icon :component="SortFilled" />
            </template>
            {{ setting.tocSortReverse ? '倒序' : '正序' }}
          </n-button>
        </SectionHeader>

        <n-list style="background-color: #0000">
          <n-card
            v-if="lastReadChapter"
            :bordered="false"
            embedded
            style="margin-bottom: 8px"
            content-style="padding: 6px 0px 0px;"
          >
            <b style="padding-left: 6px">上次读到:</b>
            <component
              :is="isDesktop ? ReuseTocItemDesktop : ReuseTocItemMobile"
              :item="lastReadChapter"
            />
          </n-card>
          <template v-if="isDesktop">
            <n-list-item
              v-for="tocItem in setting.tocSortReverse
                ? toc.slice().reverse()
                : toc"
              :key="tocItem.index"
              style="padding: 0px"
            >
              <ReuseTocItemDesktop :item="tocItem" />
            </n-list-item>
          </template>
          <template v-else>
            <n-list-item
              v-for="tocItem in setting.tocSortReverse
                ? toc.slice().reverse()
                : toc"
              :key="tocItem.index"
              style="padding: 0px"
            >
              <ReuseTocItemMobile :item="tocItem" />
            </n-list-item>
          </template>
        </n-list>
      </section>

      <CommentList :site="`web-${providerId}-${novelId}`" />
    </ResultView>
  </div>
</template>

<style scoped>
.toc:visited {
  color: color-mix(in srgb, v-bind('vars.primaryColor') 50%, red);
}
</style>
