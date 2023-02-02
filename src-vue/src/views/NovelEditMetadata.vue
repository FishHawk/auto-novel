<script lang="ts" setup>
import { onMounted, Ref, ref, shallowRef } from 'vue';
import { useRoute } from 'vue-router';
import { useMessage } from 'naive-ui';
import { UploadFilled } from '@vicons/material';

import { Ok, ResultState } from '../api/result';
import ApiNovel from '../api/api_novel';
import ApiNovelEdit from '../api/api_patch';
import { buildMetadataUrl } from '../data/provider';
import { errorToString } from '../data/handle_error';

interface EditField {
  jp: string;
  zh?: string;
  ref: Ref<string | undefined>;
}

interface EditMetadata {
  title: EditField;
  introduction: EditField;
  toc: EditField[];
}

const route = useRoute();
const providerId = route.params.providerId as string;
const bookId = route.params.bookId as string;
const url = buildMetadataUrl(providerId, bookId);

const message = useMessage();

const editMetadataRef = shallowRef<ResultState<EditMetadata>>();

onMounted(() => getMetadata());
async function getMetadata() {
  const result = await ApiNovel.getMetadata(providerId, bookId);
  if (result.ok) {
    document.title = `编辑 - ${result.value.titleJp}`;
    const bookMetadata = result.value;
    const tocSet = new Set();
    const editMetadata: EditMetadata = {
      title: {
        jp: bookMetadata.titleJp,
        zh: bookMetadata.titleZh,
        ref: ref(bookMetadata.titleZh),
      },
      introduction: {
        jp: bookMetadata.introductionJp,
        zh: bookMetadata.introductionZh,
        ref: ref(bookMetadata.introductionZh),
      },
      toc: bookMetadata.toc
        .filter((item) => {
          const inTocSet = tocSet.has(item.titleJp);
          if (!inTocSet) tocSet.add(item.titleJp);
          return !inTocSet;
        })
        .map((item) => ({
          jp: item.titleJp,
          zh: item.titleZh,
          ref: ref(item.titleZh),
        })),
    };
    editMetadataRef.value = Ok(editMetadata);
  } else {
    editMetadataRef.value = result;
  }
}

async function submitTranslate() {
  if (!editMetadataRef.value?.ok) return;
  const editMetadata = editMetadataRef.value.value;
  const patch = {
    title:
      editMetadata.title.ref.value != editMetadata.title.zh
        ? editMetadata.title.ref.value
        : undefined,
    introduction:
      editMetadata.introduction.ref.value != editMetadata.introduction.zh
        ? editMetadata.introduction.ref.value
        : undefined,
    toc: Object.assign(
      {},
      ...editMetadata.toc
        .filter((item) => item.ref.value != item.zh)
        .map((item) => ({ [item.jp]: item.ref.value }))
    ),
  };
  const result = await ApiNovelEdit.postMetadataPatch(
    providerId,
    bookId,
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
  <div v-if="editMetadataRef?.ok" class="content">
    <n-h2 style="text-align: center; width: 100%">
      <n-a :href="url" target="_blank">{{
        editMetadataRef.value.title.jp
      }}</n-a>
      <br />
      <span style="color: grey">{{ editMetadataRef.value.title.zh }}</span>
    </n-h2>

    <n-divider />

    <n-h2 prefix="bar" align-text>标题/简介</n-h2>
    <n-p>{{ editMetadataRef.value.title.jp }}</n-p>
    <n-input
      v-model:value="editMetadataRef.value.title.ref.value"
      :placeholder="editMetadataRef.value.title.jp"
    />

    <n-p>{{ editMetadataRef.value.introduction.jp }}</n-p>
    <n-input
      v-model:value="editMetadataRef.value.introduction.ref.value"
      :placeholder="editMetadataRef.value.introduction.jp"
      type="textarea"
    />

    <n-h2 prefix="bar" align-text>目录</n-h2>
    <table>
      <tr v-for="token in editMetadataRef.value.toc">
        <td style="width: 50%">{{ token.jp }}</td>
        <td>
          <n-input v-model:value="token.ref.value" :placeholder="token.jp" />
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

  <div v-if="editMetadataRef && !editMetadataRef.ok">
    <n-result
      status="error"
      title="加载错误"
      :description="errorToString(editMetadataRef.error)"
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
