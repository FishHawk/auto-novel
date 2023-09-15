<script lang="ts" setup>
import { createReusableTemplate } from '@vueuse/core';

import { WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { buildMetadataUrl, tryTranslateKeyword } from '@/data/provider';

const [DefineTag, ReuseTag] = createReusableTemplate<{
  tag: string;
  isAttention: boolean;
}>();

defineProps<{
  items: WebNovelOutlineDto[];
}>();
</script>

<template>
  <DefineTag v-slot="{ tag, isAttention }">
    <RouterNA :to="`/novel-list?query=${tag}\$`">
      <n-text depth="3">
        <component :is="isAttention ? 'b' : 'span'">
          {{ isAttention ? tag : tryTranslateKeyword(tag) }}
        </component>
      </n-text>
    </RouterNA>
    /
  </DefineTag>

  <div v-for="item in items">
    <n-h3 class="title" style="margin-bottom: 4px">
      <RouterNA :to="`/novel/${item.providerId}/${item.novelId}`">
        {{ item.titleJp }}
      </RouterNA>
    </n-h3>
    <div>{{ item.titleZh }}</div>
    <n-a :href="buildMetadataUrl(item.providerId, item.novelId)">{{
      item.providerId + '.' + item.novelId
    }}</n-a>

    <div style="color: #666">
      <template v-if="item.extra">
        {{ item.extra }}
        <br />
      </template>

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

      <template v-if="item.total">
        <br />
        {{ item.type ? item.type + ' / ' : '' }}
        总计{{ item.total }} / 百度{{ item.baidu }} / 有道{{ item.youdao }} /
        GPT3
        {{ item.gpt }}
      </template>

      <template v-if="item.updateAt">
        <br />
        本站更新于<n-time :time="item.updateAt * 1000" type="relative" />
      </template>
    </div>
    <n-divider />
  </div>
</template>
