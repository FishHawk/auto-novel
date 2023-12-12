<script lang="ts" setup>
import { UploadFilled } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ApiWebNovel, WebNovelDto } from '@/data/api/api_web_novel';
import { useIsDesktop } from '@/data/util';

const route = useRoute();
const router = useRouter();
const isDesktop = useIsDesktop(850);
const message = useMessage();

const providerId = route.params.providerId as string;
const novelId = route.params.novelId as string;

const novel = ref<WebNovelDto>();

const formValue = ref({
  title: '',
  introduction: '',
  wenkuId: '',
  toc: <{ jp: string; zh: string }[]>[],
});

onMounted(async () => {
  const result = await ApiWebNovel.getNovel(providerId, novelId);
  if (result.ok) {
    novel.value = result.value;

    const tocSet = new Set();
    formValue.value = {
      title: result.value.titleZh ?? '',
      introduction: result.value.introductionZh ?? '',
      wenkuId: result.value.wenkuId ?? '',
      toc: result.value.toc
        .filter((item) => {
          const inSet = tocSet.has(item.titleJp);
          if (!inSet) tocSet.add(item.titleJp);
          return !inSet;
        })
        .map((item) => ({
          jp: item.titleJp,
          zh: item.titleZh ?? '',
        })),
    };
  } else {
    message.error('载入失败');
  }
});

const submit = async () => {
  if (novel.value === undefined) return;

  const result = await ApiWebNovel.updateNovel(providerId, novelId, {
    title: formValue.value.title.trim(),
    introduction: formValue.value.introduction.trim(),
    wenkuId: formValue.value.wenkuId.trim(),
    toc: Object.assign(
      {},
      ...formValue.value.toc.map((item) => ({ [item.jp]: item.zh }))
    ),
  });
  if (result.ok) {
    message.success('编辑成功');
    router.push({ path: `/novel/${providerId}/${novelId}` });
  } else {
    message.error('编辑失败：' + result.error.message);
  }
};
</script>

<template>
  <MainLayout>
    <n-h1>编辑网络小说</n-h1>

    <n-form
      ref="formRef"
      :model="formValue"
      :label-placement="isDesktop ? 'left' : 'top'"
      label-width="auto"
    >
      <n-form-item label="日文标题">
        {{ novel?.titleJp }}
      </n-form-item>
      <n-form-item path="title" label="中文标题">
        <n-input
          v-model:value="formValue.title"
          :placeholder="novel?.titleJp"
          :input-props="{ spellcheck: false }"
        />
      </n-form-item>

      <n-form-item label="日文简介">
        {{ novel?.introductionJp }}
      </n-form-item>
      <n-form-item path="introduction" label="中文简介">
        <n-input
          v-model:value="formValue.introduction"
          :placeholder="novel?.introductionJp"
          :input-props="{ spellcheck: false }"
          :autosize="{ minRows: 3, maxRows: 10 }"
          type="textarea"
        />
      </n-form-item>

      <n-form-item path="wenkuId" label="文库链接">
        <n-input-group>
          <n-input-group-label>wenku/</n-input-group-label>
          <n-input
            v-model:value="formValue.wenkuId"
            placeholder="文库版ID"
            :input-props="{ spellcheck: false }"
          />
        </n-input-group>
      </n-form-item>
    </n-form>

    <n-h2 prefix="bar">目录</n-h2>
    <n-table :bordered="false" :bottom-bordered="false" style="width: 100%">
      <tr v-for="token in formValue.toc">
        <td style="width: 50%; padding: 4px">
          {{ token.jp }}
          <br />
          <n-input
            v-if="!isDesktop"
            v-model:value="token.zh"
            :placeholder="token.jp"
            :input-props="{ spellcheck: false }"
          />
        </td>
        <td v-if="isDesktop" style="padding: 4px">
          <n-input
            v-model:value="token.zh"
            :placeholder="token.jp"
            :input-props="{ spellcheck: false }"
          />
        </td>
      </tr>
    </n-table>

    <n-divider />

    <async-button type="primary" @async-click="submit">
      <template #icon>
        <n-icon :component="UploadFilled" />
      </template>
      提交
    </async-button>
  </MainLayout>
</template>
