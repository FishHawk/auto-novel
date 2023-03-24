<script lang="ts" setup>
import { ref } from 'vue';
import { useMessage } from 'naive-ui';
import { UploadFilled } from '@vicons/material';

import ApiNovel, { BookEpisodeDto } from '../data/api/api_novel';
import { useAuthInfoStore } from '../data/stores/authInfo';

const props = defineProps<{
  providerId: string;
  bookId: string;
  episodeId: string;
  bookEpisode: BookEpisodeDto;
}>();

const emit = defineEmits<{
  (e: 'update:bookEpisode', bookEpisode: BookEpisodeDto): void;
}>();

const authInfoStore = useAuthInfoStore();

const message = useMessage();

interface EditField {
  index: number;
  jp: string;
  zh: string;
  edit?: string;
}

interface EditEpisode {
  paragraphs: EditField[];
}

function createEditEpisode(): EditEpisode {
  return {
    paragraphs: props.bookEpisode.paragraphsJp
      .map((textJp, index) => ({ textJp, index }))
      .filter((item) => item.textJp.trim())
      .map((item) => ({
        index: item.index,
        jp: item.textJp,
        zh: props.bookEpisode.paragraphsZh!.at(item.index)!,
        edit: props.bookEpisode.paragraphsZh!.at(item.index)!,
      })),
  };
}

const editEpisode = ref<EditEpisode>(createEditEpisode());

const isSubmitting = ref(false);

async function submitTranslate() {
  if (isSubmitting.value) return;
  isSubmitting.value = true;

  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }

  const patch = {
    paragraphs: Object.assign(
      {},
      ...editEpisode.value.paragraphs
        .filter((item) => item.edit != item.zh)
        .map((item) => ({ [item.index]: item.edit }))
    ),
  };
  const result = await ApiNovel.putEpisode(
    props.providerId,
    props.bookId,
    props.episodeId,
    patch,
    token
  );

  isSubmitting.value = false;

  if (result.ok) {
    emit('update:bookEpisode', result.value);
    message.success('提交成功');
  } else {
    message.error('提交失败：' + result.error.message);
  }
}
</script>

<template>
  <table>
    <tr v-for="paragraph in editEpisode.paragraphs">
      <td>{{ paragraph.jp }}</td>
      <td>
        <n-input
          v-model:value="paragraph.edit"
          :placeholder="paragraph.jp"
          type="textarea"
          autosize
        />
      </td>
    </tr>
  </table>

  <n-button
    round
    size="large"
    type="primary"
    class="float"
    :loading="isSubmitting"
    @click="submitTranslate()"
  >
    <template #icon>
      <n-icon><UploadFilled /></n-icon>
    </template>
    提交翻译
  </n-button>
</template>

<style scoped>
td {
  width: 50%;
  text-align: left;
  vertical-align: top;
  height: 100%;
}
table {
  width: 100%;
  height: 1px;
  border-collapse: separate;
  border-spacing: 0 1em;
}
tr {
  height: 100%;
}
td > div {
  height: 100%;
}
.float {
  position: fixed;
  right: 40px;
  bottom: 40px;
  box-shadow: rgb(0 0 0 / 12%) 0px 2px 8px 0px;
}
.content {
  width: 800px;
  margin: 0 auto;
  padding-left: 24px;
  padding-right: 24px;
  padding-bottom: 48px;
}
@media only screen and (max-width: 600px) {
  .content {
    width: auto;
    padding-left: 10px;
    padding-right: 10px;
    padding-bottom: 48px;
  }
}
</style>
