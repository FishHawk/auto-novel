<script lang="ts" setup>
import { ref } from 'vue';
import { useMessage } from 'naive-ui';
import { UploadFilled } from '@vicons/material';

import { ApiWebNovel, WebNovelMetadataDto } from '@/data/api/api_web_novel';
import { useUserDataStore } from '@/data/stores/user_data';
import { useIsDesktop } from '@/data/util';

const props = defineProps<{
  providerId: string;
  novelId: string;
  novelMetadata: WebNovelMetadataDto;
}>();

const emit = defineEmits<{
  (e: 'update:novelMetadata', novelMetadata: WebNovelMetadataDto): void;
}>();

const isDesktop = useIsDesktop(850);
const userData = useUserDataStore();

interface EditField {
  jp: string;
  zh?: string;
  edit?: string;
}

interface EditMetadata {
  title: EditField;
  introduction: EditField;
  toc: EditField[];
}

function createEditMetadata(): EditMetadata {
  const tocSet = new Set();
  return {
    title: {
      jp: props.novelMetadata.titleJp,
      zh: props.novelMetadata.titleZh,
      edit: props.novelMetadata.titleZh ?? '',
    },
    introduction: {
      jp: props.novelMetadata.introductionJp,
      zh: props.novelMetadata.introductionZh,
      edit: props.novelMetadata.introductionZh ?? '',
    },
    toc: props.novelMetadata.toc
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

const isSubmitting = ref(false);

async function submit() {
  if (isSubmitting.value) return;
  isSubmitting.value = true;

  if (!userData.isLoggedIn) {
    message.info('请先登录');
    return;
  }

  function ifEdited(field: EditField) {
    return field.edit?.trim() && field.zh != field.edit
      ? field.edit
      : undefined;
  }

  const patch = {
    title: ifEdited(editMetadata.value.title),
    introduction: ifEdited(editMetadata.value.introduction),
    toc: Object.assign(
      {},
      ...editMetadata.value.toc
        .filter((item) => ifEdited(item))
        .map((item) => ({ [item.jp]: ifEdited(item) }))
    ),
  };
  const result = await ApiWebNovel.updateMetadata(
    props.providerId,
    props.novelId,
    patch
  );

  isSubmitting.value = false;

  if (result.ok) {
    emit('update:novelMetadata', result.value);
    message.success('提交成功');
  } else {
    message.error('提交失败：' + result.error.message);
  }
}

const wenkuId = ref(props.novelMetadata.wenkuId);
async function updateWenkuId() {
  if (!wenkuId.value) {
    message.info('文库版Id不能为空');
    return;
  }
  if (!userData.isLoggedIn) {
    message.info('请先登录');
    return;
  }
  const result = await ApiWebNovel.putWenkuId(
    props.providerId,
    props.novelId,
    wenkuId.value
  );
  if (result.ok) {
    message.success('提交成功');
  } else {
    message.error('提交失败：' + result.error.message);
  }
}

async function deleteWenkuId() {
  if (!userData.isLoggedIn) {
    message.info('请先登录');
    return;
  }
  const result = await ApiWebNovel.deleteWenkuId(
    props.providerId,
    props.novelId
  );
  if (result.ok) {
    message.success('提交成功');
  } else {
    message.error('提交失败：' + result.error.message);
  }
}
</script>

<template>
  <n-p>
    <n-input-group>
      <n-input-group-label>books.fishhawk.top/wenku/</n-input-group-label>
      <n-input v-model:value="wenkuId" placeholder="文库版ID" />
      <n-button @click="updateWenkuId()">更新</n-button>
      <n-button @click="deleteWenkuId()">删除</n-button>
    </n-input-group>
  </n-p>

  <n-p>{{ editMetadata.title.jp }}</n-p>
  <n-input
    v-model:value="editMetadata.title.edit"
    :placeholder="editMetadata.title.jp"
    :input-props="{ spellcheck: false }"
  />

  <n-p>{{ editMetadata.introduction.jp }}</n-p>
  <n-input
    v-model:value="editMetadata.introduction.edit"
    :placeholder="editMetadata.introduction.jp"
    :autosize="{
      minRows: 3,
      maxRows: 10,
    }"
    type="textarea"
    :input-props="{ spellcheck: false }"
  />

  <n-h2 prefix="bar">目录</n-h2>
  <table style="width: 100%">
    <template v-for="token in editMetadata.toc">
      <tr>
        <td style="width: 50%; padding: 4px">
          {{ token.jp }}
          <br />
          <n-input
            v-if="!isDesktop"
            v-model:value="token.edit"
            :placeholder="token.jp"
            :input-props="{ spellcheck: false }"
          />
        </td>
        <td v-if="isDesktop" style="padding: 4px">
          <n-input
            v-model:value="token.edit"
            :placeholder="token.jp"
            :input-props="{ spellcheck: false }"
          />
        </td>
      </tr>
      <n-divider v-if="isDesktop" style="width: 200%; margin: 0px" />
      <n-divider v-if="!isDesktop" style="width: 100%; margin: 0px" />
    </template>
  </table>

  <n-button
    round
    size="large"
    type="primary"
    class="float"
    :loading="isSubmitting"
    @click="submit()"
  >
    <template #icon>
      <n-icon><UploadFilled /></n-icon>
    </template>
    提交
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
