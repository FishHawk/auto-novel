<script lang="ts" setup>
import { WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { tryTranslateKeywords } from '@/data/keyword_translate';
import { buildMetadataUrl } from '@/data/provider';

defineProps<{
  items: WebNovelOutlineDto[];
}>();
</script>

<template>
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

    <template v-if="item.extra">
      <div v-for="extraLine in item.extra.split('\n')" style="color: #666">
        {{ extraLine }}
      </div>
    </template>

    <template v-else>
      <div style="color: #666">
        <template v-for="attention in item.attentions.sort()">
          <b>{{ attention }}</b> /
        </template>
        <template v-for="keyword in tryTranslateKeywords(item.keywords)">
          {{ keyword }} /
        </template>
      </div>
    </template>

    <template v-if="item.total">
      <div style="color: #666">
        {{ item.type ? item.type + ' / ' : '' }}
        总计{{ item.total }} / 百度{{ item.baidu }} / 有道{{ item.youdao }}
      </div>
    </template>
    <n-divider />
  </div>
</template>
