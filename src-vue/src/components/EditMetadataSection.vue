<script lang="ts" setup>
import { ref } from 'vue';
import { useMessage } from 'naive-ui';
import { UploadFilled } from '@vicons/material';

import ApiWebNovel, { BookMetadataDto } from '../data/api/api_web_novel';
import { useAuthInfoStore } from '../data/stores/authInfo';

const props = defineProps<{
  providerId: string;
  bookId: string;
  bookMetadata: BookMetadataDto;
}>();

const emit = defineEmits<{
  (e: 'update:bookMetadata', bookMetadata: BookMetadataDto): void;
}>();

const authInfoStore = useAuthInfoStore();

interface EditField {
  jp: string;
  zh?: string;
  edit?: string;
}
interface EditGlossary {
  origin: { [key: string]: string };
  edit: { [key: string]: string };
}

interface EditMetadata {
  title: EditField;
  introduction: EditField;
  glossary: EditGlossary;
  toc: EditField[];
}

function createEditMetadata(): EditMetadata {
  const tocSet = new Set();
  return {
    title: {
      jp: props.bookMetadata.titleJp,
      zh: props.bookMetadata.titleZh,
      edit: props.bookMetadata.titleZh ?? '',
    },
    introduction: {
      jp: props.bookMetadata.introductionJp,
      zh: props.bookMetadata.introductionZh,
      edit: props.bookMetadata.introductionZh ?? '',
    },
    glossary: {
      origin: props.bookMetadata.glossary,
      edit: props.bookMetadata.glossary,
    },
    toc: props.bookMetadata.toc
      .filter((item) => {
        const inTocSet = tocSet.has(item.titleJp);
        if (!inTocSet) tocSet.add(item.titleJp);
        return !inTocSet;
      })
      .map((item) => ({
        jp: item.titleJp,
        zh: item.titleZh,
        edit: item.titleZh ?? '',
      })),
  };
}

const message = useMessage();

const editMetadata = ref(createEditMetadata());
const termsToAdd = ref(['', '']);

const isSubmitting = ref(false);

async function submitTranslate() {
  if (isSubmitting.value) return;
  isSubmitting.value = true;

  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }

  function ifEdited(field: EditField) {
    return field.zh != field.edit ? field.edit : undefined;
  }

  const patch = {
    title: ifEdited(editMetadata.value.title),
    introduction: ifEdited(editMetadata.value.introduction),
    glossary: editMetadata.value.glossary.edit,
    toc: Object.assign(
      {},
      ...editMetadata.value.toc
        .filter((item) => item.zh != item.edit)
        .map((item) => ({ [item.jp]: item.edit }))
    ),
  };
  const result = await ApiWebNovel.putMetadata(
    props.providerId,
    props.bookId,
    patch,
    token
  );

  isSubmitting.value = false;

  if (result.ok) {
    emit('update:bookMetadata', result.value);
    message.success('提交成功');
  } else {
    message.error('提交失败：' + result.error.message);
  }
}

function deleteTerm(jp: string) {
  delete editMetadata.value.glossary.edit[jp];
  editMetadata.value.glossary = editMetadata.value.glossary;
}

function addTerm() {
  const jp = termsToAdd.value[0];
  const zh = termsToAdd.value[1];
  if (jp && zh) {
    editMetadata.value.glossary.edit[jp] = zh;
    termsToAdd.value = ['', ''];
  }
}
</script>

<template>
  <n-p>{{ editMetadata.title.jp }}</n-p>
  <n-input
    v-model:value="editMetadata.title.edit"
    :placeholder="editMetadata.title.jp"
  />

  <n-p>{{ editMetadata.introduction.jp }}</n-p>
  <n-input
    v-model:value="editMetadata.introduction.edit"
    :placeholder="editMetadata.introduction.jp"
    type="textarea"
  />

  <n-h2 prefix="bar">术语表</n-h2>
  <n-p>在你使用术语表之前需要知道的：</n-p>
  <ui>
    <li>
      <span>
        术语表的原理是在翻译前将日文词替换成随机字母，在翻译后替换回对应中文词。
      </span>
    </li>
    <li>
      <span>
        在修改术语表后再次更新中文时，已经翻译的章节会按照新的术语表重新翻译需要更新的段落。
      </span>
    </li>
    <li>
      <span>
        术语表会影响ai对词义的理解，例如：无法从人名判断性别导致ai使用了错误的人称代词。
      </span>
    </li>
  </ui>
  <n-p> 总而言之,术语表不是万能的，请只在有必要的情况下编辑术语表。 </n-p>
  <table style="border-spacing: 16px 0px">
    <tr v-for="(termZh, termJp) in editMetadata.glossary.edit">
      <td>{{ termJp }}</td>
      <td style="width: 4px">=></td>
      <td>{{ termZh }}</td>
      <td>
        <n-button @click="deleteTerm(termJp as string)">删除</n-button>
      </td>
    </tr>
    <tr>
      <td colspan="3">
        <n-input
          pair
          v-model:value="termsToAdd"
          separator="=>"
          :placeholder="['日文', '中文']"
          clearable
        />
      </td>
      <td>
        <n-button @click="addTerm()">添加</n-button>
      </td>
    </tr>
  </table>

  <n-h2 prefix="bar">目录</n-h2>
  <table style="width: 100%">
    <template v-for="token in editMetadata.toc">
      <tr>
        <td style="width: 50%; padding: 4px">
          {{ token.jp }}
          <br />
          <n-input
            class="on-mobile"
            v-model:value="token.edit"
            :placeholder="token.jp"
          />
        </td>
        <td class="on-desktop" style="padding: 4px">
          <n-input v-model:value="token.edit" :placeholder="token.jp" />
        </td>
      </tr>
      <n-divider class="on-desktop" style="width: 200%; margin: 0px" />
      <n-divider class="on-mobile" style="width: 100%; margin: 0px" />
    </template>
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
.float {
  position: fixed;
  right: 40px;
  bottom: 40px;
  box-shadow: rgb(0 0 0 / 12%) 0px 2px 8px 0px;
}
</style>
