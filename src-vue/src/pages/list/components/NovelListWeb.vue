<script lang="ts" setup>
import { createReusableTemplate } from '@vueuse/core';

import { WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { tryTranslateKeyword } from '@/data/keyword_translate';
import { buildMetadataUrl } from '@/data/provider';

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
    <n-a :href="`/novel-list?query=${tag}\$`" style="color: #666">
      <component :is="isAttention ? 'b' : 'span'">
        {{ isAttention ? tag : tryTranslateKeyword(tag) }}
      </component>
    </n-a>
    /
  </DefineTag>

  <div v-for="item in items">
    <n-h3 class="title" style="margin-bottom: 4px">
      <n-a :href="`/novel/${item.providerId}/${item.novelId}`" target="_blank">
        {{ item.titleJp }}
      </n-a>
    </n-h3>
    <div>{{ item.titleZh }}</div>
    <n-a
      :href="buildMetadataUrl(item.providerId, item.novelId)"
      target="_blank"
    >
      {{ item.providerId + '.' + item.novelId }}
    </n-a>

    <div v-if="item.extra" style="color: #666">
      {{ item.extra }}
    </div>

    <div style="color: #666">
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
    </div>

    <template v-if="item.total">
      <div style="color: #666">
        {{ item.type ? item.type + ' / ' : '' }}
        总计{{ item.total }} / 百度{{ item.baidu }} / 有道{{ item.youdao }} /
        GPT3 {{ item.gpt }}
      </div>
    </template>
    <n-divider />
  </div>
</template>
