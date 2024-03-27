<script lang="ts" setup>
import { createReusableTemplate } from '@vueuse/core';

import { tryTranslateKeyword } from '@/data/web/keyword';
import { buildWebNovelUrl } from '@/data/web/url';
import { WebNovelOutlineDto } from '@/model/WebNovel';

const [DefineTag, ReuseTag] = createReusableTemplate<{
  tag: string;
  isAttention: boolean;
}>();

const props = defineProps<{
  simple?: boolean;
  items: WebNovelOutlineDto[];
  selectable?: boolean;
}>();

const selectedNovels = ref<string[]>([]);

watch(
  () => props.selectable,
  (selectable) => {
    if (selectable !== false) {
      selectedNovels.value = [];
    }
  }
);
const toggleNovelSelect = (novel: string, selected: boolean) => {
  if (!selected) {
    selectedNovels.value = selectedNovels.value.filter((it) => it != novel);
  } else if (!selectedNovels.value.includes(novel)) {
    selectedNovels.value.push(novel);
  }
};

const getSelectedNovels = () => {
  return props.items.filter((it) =>
    selectedNovels.value.includes(`${it.providerId}/${it.novelId}`)
  );
};

const selectAll = () => {
  selectedNovels.value = props.items.map(
    (it) => `${it.providerId}/${it.novelId}`
  );
};

const invertSelection = () => {
  selectedNovels.value = props.items
    .map((it) => `${it.providerId}/${it.novelId}`)
    .filter((it) => !selectedNovels.value.includes(it));
};

defineExpose({
  getSelectedNovels,
  selectAll,
  invertSelection,
});
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
      <n-checkbox
        v-if="selectable"
        :checked="selectedNovels.includes(`${item.providerId}/${item.novelId}`)"
        @update:checked="
          (selected: boolean) =>
            toggleNovelSelect(`${item.providerId}/${item.novelId}`, selected)
        "
        style="margin-right: 8px"
      />
      <c-a :to="`/novel/${item.providerId}/${item.novelId}`">
        {{ item.titleJp }}
      </c-a>
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
