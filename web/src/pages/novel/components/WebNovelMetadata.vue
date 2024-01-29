<script lang="ts" setup>
import { BookFilled, CommentFilled, EditNoteFilled } from '@vicons/material';
import { NA, NText } from 'naive-ui';

import { buildWebNovelUrl } from '@/data/util_web';

import { WebNovelVM } from './common';

defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelVM;
}>();

const emit = defineEmits<{
  commentClick: [];
}>();
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

    <n-button @click="emit('commentClick')">
      <template #icon>
        <n-icon :component="CommentFilled" />
      </template>
      评论
    </n-button>
  </n-flex>

  <n-p>{{ novel.type }} / 浏览次数:{{ novel.visited }}</n-p>

  <n-p style="word-break: break-all">
    {{ novel.introductionJp }}
  </n-p>
  <n-p v-if="novel.introductionZh !== undefined" style="word-break: break-all">
    {{ novel.introductionZh }}
  </n-p>

  <n-flex :size="[4, 4]">
    <web-novel-tag
      v-for="attention of novel.attentions.sort()"
      :tag="attention"
      :attention="true"
    />
    <web-novel-tag
      v-for="keyword of novel.keywords"
      :tag="keyword"
      :attention="false"
    />
  </n-flex>
</template>
