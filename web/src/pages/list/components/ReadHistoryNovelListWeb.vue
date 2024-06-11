<script lang="ts" setup>
import { WebNovelOutlineDto } from '@/model/WebNovel';
import { WebUtil } from '@/util/web';

defineProps<{
  items: WebNovelOutlineDto[];
}>();

const progressPercentage = (it: WebNovelOutlineDto): number => {
  if (!it?.read) {
    return 0;
  }
  return (it.read / it.total) * 100;
};
</script>

<template>
  <n-timeline>
    <n-timeline-item v-for="item of items">
      <n-flex
        align="center"
        justify="space-between"
        :wrap="false"
        style="width: 100"
      >
        <div>
          <c-a :to="`/novel/${item.providerId}/${item.novelId}`">
            {{ item.titleJp }}
          </c-a>
          <br />

          <template v-if="item.titleZh">
            {{ item.titleZh }}
            <br />
          </template>

          <n-a :href="WebUtil.buildNovelUrl(item.providerId, item.novelId)">
            {{ item.providerId + ': ' + item.novelId }}
          </n-a>

          <!-- <n-time v-if="item.readAt" :time="item.readAt" /> -->

          <n-text depth="3" tag="div">
            <template v-if="item.extra">
              {{ item.extra }}
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

            <template v-if="item.readAt">
              阅读时间<n-time :time="item.readAt * 1000" type="relative" />
              <br />
            </template>
          </n-text>
        </div>
        <n-flex :wrap="false">
          <slot name="action" v-bind="item" />
        </n-flex>
      </n-flex>
      <n-progress
        :percentage="progressPercentage(item)"
        style="max-width: 400px"
      />
    </n-timeline-item>
  </n-timeline>
</template>
