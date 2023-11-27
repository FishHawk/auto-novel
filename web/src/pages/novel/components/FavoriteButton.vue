<script lang="ts" setup>
import { FavoriteBorderFilled, FavoriteFilled } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import { ApiUser, Favored } from '@/data/api/api_user';

const { favored, favoredList, novel } = defineProps<{
  favored: string | undefined;
  favoredList: Favored[];
  novel:
    | { type: 'web'; providerId: string; novelId: string }
    | { type: 'wenku'; novelId: string };
}>();
const emit = defineEmits<{ 'update:favored': [string | undefined] }>();

const message = useMessage();

const favoredTitle = computed(
  () => favoredList.find((it) => it.id === favored)?.title
);

async function favoriteNovel(favoredId: string) {
  const result = await (novel.type === 'web'
    ? ApiUser.favoriteWebNovel(favoredId, novel.providerId, novel.novelId)
    : ApiUser.favoriteWenkuNovel(favoredId, novel.novelId));
  if (result.ok) {
    emit('update:favored', favoredId);
    showFavoredModal.value = false;
  } else {
    message.error('收藏错误：' + result.error.message);
  }
}

async function unfavoriteNovel() {
  if (favored === undefined) return;
  const result = await (novel.type === 'web'
    ? ApiUser.unfavoriteWebNovel(favored, novel.providerId, novel.novelId)
    : ApiUser.unfavoriteWenkuNovel(favored, novel.novelId));
  if (result.ok) {
    emit('update:favored', undefined);
    showFavoredModal.value = false;
  } else {
    message.error('取消收藏错误：' + result.error.message);
  }
}

const showFavoredModal = ref(false);
const selectedFavoredId = ref(favored ?? 'default');

const requestLoginFirst = () => message.info('请先登录');
</script>

<template>
  <n-button v-if="favoredList.length === 0" @click="requestLoginFirst()">
    <template #icon>
      <n-icon :component="FavoriteBorderFilled" />
    </template>
    收藏
  </n-button>

  <template v-else-if="favoredList.length === 1">
    <async-button v-if="favored" @async-click="unfavoriteNovel">
      <template #icon>
        <n-icon :component="FavoriteFilled" />
      </template>
      已收藏
    </async-button>
    <async-button v-else @async-click="() => favoriteNovel(favoredList[0].id)">
      <template #icon>
        <n-icon :component="FavoriteBorderFilled" />
      </template>
      收藏
    </async-button>
  </template>

  <template v-else>
    <n-button @click="() => (showFavoredModal = true)">
      <template #icon>
        <n-icon :component="favored ? FavoriteFilled : FavoriteBorderFilled" />
      </template>
      {{ favored ? '已收藏:' + favoredTitle : '收藏' }}
    </n-button>
  </template>

  <card-modal v-model:show="showFavoredModal" title="收藏到...">
    <n-radio-group v-model:value="selectedFavoredId">
      <n-space vertical>
        <n-radio
          v-for="favored in favoredList"
          :key="favored.id"
          :value="favored.id"
        >
          {{ favored.title }}
        </n-radio>
        <n-radio key="deleted" value="deleted"> 取消收藏 </n-radio>
      </n-space>
    </n-radio-group>
    <template #action>
      <async-button
        type="primary"
        @async-click="
          () =>
            selectedFavoredId === 'deleted'
              ? unfavoriteNovel()
              : favoriteNovel(selectedFavoredId)
        "
      >
        确定
      </async-button>
    </template>
  </card-modal>
</template>
