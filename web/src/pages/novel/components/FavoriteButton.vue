<script lang="ts" setup>
import { FavoriteBorderFilled, FavoriteFilled } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import { ApiUser, Favored } from '@/data/api/api_user';

const props = defineProps<{
  favored: string | undefined;
  favoredList: Favored[];
  novel:
    | { type: 'web'; providerId: string; novelId: string }
    | { type: 'wenku'; novelId: string };
}>();
const emit = defineEmits<{ 'update:favored': [string | undefined] }>();

const message = useMessage();

const favoredTitle = computed(
  () => props.favoredList.find((it) => it.id === props.favored)?.title
);

const favoriteNovel = async (favoredId: string) => {
  const result = await (props.novel.type === 'web'
    ? ApiUser.favoriteWebNovel(
        favoredId,
        props.novel.providerId,
        props.novel.novelId
      )
    : ApiUser.favoriteWenkuNovel(favoredId, props.novel.novelId));
  if (result.ok) {
    emit('update:favored', favoredId);
    message.success('收藏成功');
    showFavoredModal.value = false;
  } else {
    message.error('收藏错误：' + result.error.message);
  }
};

const unfavoriteNovel = async () => {
  if (props.favored === undefined) return;
  const result = await (props.novel.type === 'web'
    ? ApiUser.unfavoriteWebNovel(
        props.favored,
        props.novel.providerId,
        props.novel.novelId
      )
    : ApiUser.unfavoriteWenkuNovel(props.favored, props.novel.novelId));
  if (result.ok) {
    emit('update:favored', undefined);
    message.success('取消收藏成功');
    showFavoredModal.value = false;
  } else {
    message.error('取消收藏错误：' + result.error.message);
  }
};

const showFavoredModal = ref(false);
const selectedFavoredId = ref(props.favored ?? 'default');
</script>

<template>
  <template v-if="favoredList.length <= 1">
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
      <n-space vertical size="large">
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
