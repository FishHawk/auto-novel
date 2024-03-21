<script lang="ts" setup>
import {
  BookOutlined,
  CommentOutlined,
  EditNoteOutlined,
} from '@vicons/material';
import { NA, NText } from 'naive-ui';

import { tryTranslateKeyword } from '@/data/web/keyword';
import { buildWebNovelUrl } from '@/data/web/url';

import { WebNovelVM } from './common';

const props = defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelVM;
}>();

const emit = defineEmits<{
  commentClick: [];
}>();

const labels = computed(() => {
  const readableNumber = (num: number | undefined) => {
    if (typeof num !== 'number') return undefined;
    if (num < 1000) return num.toString();
    else return (num / 1000).toFixed(1).toString() + 'k';
  };

  const withPointDeco = (str: string | undefined) => {
    if (typeof str !== 'string') return undefined;
    if (props.providerId === 'kakuyomu') return '★' + str;
    else return str + ' PT';
  };

  const labels = [
    props.novel.type,
    withPointDeco(readableNumber(props.novel.points)),
    readableNumber(props.novel.totalCharacters) + ' 字',
    readableNumber(props.novel.visited) + ' 浏览',
  ]
    .filter(Boolean)
    .join(' / ');
  return labels;
});
</script>

<template>
  <n-h3 prefix="bar">
    <n-a :href="buildWebNovelUrl(providerId, novelId)">{{ novel.titleJp }}</n-a>
    <br />
    <n-text depth="3">{{ novel.titleZh }}</n-text>
  </n-h3>

  <n-p v-if="novel.authors.length > 0">
    作者：
    <template v-for="author in novel.authors">
      <n-a :href="author.link">{{ author.name }}</n-a>
    </template>
  </n-p>

  <n-flex>
    <router-link :to="`/novel-edit/${providerId}/${novelId}`">
      <c-button label="编辑" :icon="EditNoteOutlined" />
    </router-link>

    <favorite-button
      v-model:favored="novel.favored"
      :favored-list="novel.favoredList"
      :novel="{ type: 'web', providerId, novelId }"
    />

    <router-link v-if="novel.wenkuId" :to="`/wenku/${novel.wenkuId}`">
      <c-button label="文库" :icon="BookOutlined" />
    </router-link>

    <c-button
      label="评论"
      :icon="CommentOutlined"
      @action="emit('commentClick')"
    />
  </n-flex>

  <n-divider />

  <n-p>{{ labels }}</n-p>

  <n-p style="word-break: break-all">
    {{ novel.introductionJp }}
  </n-p>
  <n-p v-if="novel.introductionZh !== undefined" style="word-break: break-all">
    {{ novel.introductionZh }}
  </n-p>

  <n-flex :size="[4, 4]">
    <router-link
      v-for="attention of novel.attentions.sort()"
      :to="`/novel-list?query=${attention}\$`"
    >
      <novel-tag :tag="attention" strong />
    </router-link>

    <router-link
      v-for="keyword of novel.keywords"
      :to="`/novel-list?query=${keyword}\$`"
    >
      <novel-tag :tag="tryTranslateKeyword(keyword)" />
    </router-link>
  </n-flex>
</template>
