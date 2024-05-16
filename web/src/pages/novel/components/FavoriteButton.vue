<script lang="ts" setup>
import { FavoriteBorderOutlined, FavoriteOutlined } from '@vicons/material';

import { UserRepository } from '@/data/api';
import { Favored } from '@/model/User';
import { doAction } from '@/pages/util';

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
  () => props.favoredList.find((it) => it.id === props.favored)?.title,
);

const favoriteNovel = (favoredId: string) =>
  doAction(
    (props.novel.type === 'web'
      ? UserRepository.favoriteWebNovel(
          favoredId,
          props.novel.providerId,
          props.novel.novelId,
        )
      : UserRepository.favoriteWenkuNovel(favoredId, props.novel.novelId)
    ).then(() => {
      emit('update:favored', favoredId);
      showFavoredModal.value = false;
    }),
    '收藏',
    message,
  );

const unfavoriteNovel = async () => {
  if (props.favored === undefined) return;
  await doAction(
    (props.novel.type === 'web'
      ? UserRepository.unfavoriteWebNovel(
          props.favored,
          props.novel.providerId,
          props.novel.novelId,
        )
      : UserRepository.unfavoriteWenkuNovel(props.favored, props.novel.novelId)
    ).then(() => {
      emit('update:favored', undefined);
      showFavoredModal.value = false;
    }),
    '取消收藏',
    message,
  );
};

const showFavoredModal = ref(false);
const selectedFavoredId = ref(props.favored ?? 'default');
</script>

<template>
  <template v-if="favoredList.length <= 1">
    <c-button
      v-if="favored"
      label="已收藏"
      :icon="FavoriteOutlined"
      require-login
      @action="unfavoriteNovel"
    />
    <c-button
      v-else
      label="收藏"
      :icon="FavoriteBorderOutlined"
      require-login
      @action="favoriteNovel(favoredList[0].id)"
    />
  </template>

  <template v-else>
    <c-button
      :label="favored ? '已收藏:' + favoredTitle : '收藏'"
      :icon="favored ? FavoriteOutlined : FavoriteBorderOutlined"
      require-login
      @action="showFavoredModal = true"
    />
  </template>

  <c-modal v-model:show="showFavoredModal" title="收藏到...">
    <n-radio-group v-model:value="selectedFavoredId">
      <n-flex vertical size="large">
        <n-radio
          v-for="favored in favoredList"
          :key="favored.id"
          :value="favored.id"
        >
          {{ favored.title }}
        </n-radio>
        <n-radio key="deleted" value="deleted"> 取消收藏 </n-radio>
      </n-flex>
    </n-radio-group>
    <template #action>
      <c-button
        label="确定"
        require-login
        type="primary"
        @action="
          selectedFavoredId === 'deleted'
            ? unfavoriteNovel()
            : favoriteNovel(selectedFavoredId)
        "
      />
    </template>
  </c-modal>
</template>
