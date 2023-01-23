<script lang="ts" setup>
import { onMounted, Ref, ref, shallowRef } from 'vue';
import { useRoute } from 'vue-router';
import { useMessage } from 'naive-ui';
import { UploadFilled } from '@vicons/material';

import { Err, Ok, ResultState } from '../api/result';
import { buildEpisodeUrl } from '../data/provider';
import { errorToString } from '../data/handle_error';

import ApiNovel from '../api/api_novel';
import ApiNovelEdit from '../api/api_novel_edit';

const route = useRoute();
const providerId = route.params.providerId as string;
const bookId = route.params.bookId as string;
const episodeId = route.params.episodeId as string;
const url = buildEpisodeUrl(providerId, bookId, episodeId);

const message = useMessage();

interface EditField {
  index: number;
  jp: string;
  zh: string;
  ref: Ref<string | undefined>;
}

interface EditEpisode {
  titleJp: string;
  titleZh?: string;
  prevId?: string;
  nextId?: string;
  paragraphs: EditField[];
}

const editEpisodeRef = shallowRef<ResultState<EditEpisode>>();

onMounted(() => getEpisode());
async function getEpisode() {
  const result = await ApiNovel.getEpisode(providerId, bookId, episodeId);
  if (result.ok) {
    document.title = `编辑 - ${result.value.titleJp}`;
    if (result.value.paragraphsZh) {
      const bookEpisode = result.value;
      const editEpisode: EditEpisode = {
        titleJp: bookEpisode.titleJp,
        titleZh: bookEpisode.titleZh,
        prevId: bookEpisode.prevId,
        nextId: bookEpisode.nextId,
        paragraphs: bookEpisode.paragraphsJp
          .map((textJp, index) => ({ textJp, index }))
          .filter((item) => item.textJp.trim())
          .map((item) => ({
            index: item.index,
            jp: item.textJp,
            zh: bookEpisode.paragraphsZh!.at(item.index)!,
            ref: ref(bookEpisode.paragraphsZh!.at(item.index)!),
          })),
      };
      editEpisodeRef.value = Ok(editEpisode);
    } else {
      editEpisodeRef.value = Err(Error('未翻译。'));
    }
  } else {
    editEpisodeRef.value = result;
  }
}

async function submitTranslate() {
  if (!editEpisodeRef.value?.ok) return;
  const editEpisode = editEpisodeRef.value.value;
  const patch = {
    paragraphs: Object.assign(
      {},
      ...editEpisode.paragraphs
        .filter((item) => item.ref.value != item.zh)
        .map((item) => ({ [item.index]: item.ref.value }))
    ),
  };
  const result = await ApiNovelEdit.postEpisodePatch(
    providerId,
    bookId,
    episodeId,
    patch
  );
  if (result.ok) {
    message.success('提交成功');
  } else {
    message.error('提交失败：' + errorToString(result.error));
  }
}
</script>

<template>
  <div class="content" v-if="editEpisodeRef?.ok">
    <n-h2 style="text-align: center; width: 100%">
      <n-a :href="url" target="_blank">{{ editEpisodeRef.value.titleJp }}</n-a>
      <br />
      <span style="color: gray">{{ editEpisodeRef.value.titleZh }}</span>
    </n-h2>

    <n-divider />

    <n-h2 prefix="bar" align-text>段落</n-h2>
    <table>
      <tr v-for="paragraph in editEpisodeRef.value.paragraphs">
        <td>{{ paragraph.jp }}</td>
        <td>
          <n-input
            v-model:value="paragraph.ref.value"
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
      @click="submitTranslate()"
    >
      <template #icon>
        <n-icon><UploadFilled /></n-icon>
      </template>
      提交翻译
    </n-button>
  </div>

  <div v-if="editEpisodeRef && !editEpisodeRef.ok">
    <n-result
      status="error"
      title="加载错误"
      :description="errorToString(editEpisodeRef.error)"
    />
  </div>
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
</style>
