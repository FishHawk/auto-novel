<script lang="ts" setup>
import { createReusableTemplate } from '@vueuse/core';

import { WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { buildWebNovelUrl, tryTranslateKeyword } from '@/data/util_web';

const [DefineTag, ReuseTag] = createReusableTemplate<{
  tag: string;
  isAttention: boolean;
}>();

withDefaults(
  defineProps<{
    simple: boolean;
    items: WebNovelOutlineDto[];
  }>(),
  {
    simple: false,
  }
);
</script>

<template>
  <DefineTag v-slot="{ tag, isAttention }">
    <router-link :to="`/novel-list?query=${tag}\$`">
      <n-text depth="3">
        <component :is="isAttention ? 'b' : 'span'">
          {{ isAttention ? tag : tryTranslateKeyword(tag) }}
        </component>
      </n-text>
    </router-link>
    /
  </DefineTag>

  <n-list>
    <n-list-item v-for="item of items">
      <RouterNA :to="`/novel/${item.providerId}/${item.novelId}`">
        {{ item.titleJp }}
      </RouterNA>
      <br />

      <template v-if="item.titleZh">
        {{ item.titleZh }}
        <br />
      </template>

      <n-a
        v-if="!simple"
        :href="buildWebNovelUrl(item.providerId, item.novelId)"
      >
        {{ item.providerId + '.' + item.novelId }}
      </n-a>

      <n-text depth="3" tag="div">
        <template v-if="item.extra">
          {{ item.extra }}
          <br />
        </template>

        <template v-if="!simple">
          <ReuseTag
            v-for="attention in item.attentions.sort()"
            :tag="attention"
            :isAttention="true"
          />
          <ReuseTag
            v-for="keyword in item.keywords"
            :tag="keyword"
            :isAttention="false"
          />
          <br />
        </template>

        <template v-if="item.total">
          {{ item.type ? item.type + ' / ' : '' }}
          总计 {{ item.total }} / 百度 {{ item.baidu }} / 有道
          {{ item.youdao }} / GPT {{ item.gpt }} / Sakura {{ item.sakura }}
          <br />
        </template>

        <template v-if="item.updateAt">
          本站更新于<n-time :time="item.updateAt * 1000" type="relative" />
          <br />
        </template>
      </n-text>
    </n-list-item>
  </n-list>
</template>
