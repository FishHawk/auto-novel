<script lang="ts" setup>
import { UploadOutlined } from '@vicons/material';
import { FormInst, FormItemRule, FormRules, useMessage } from 'naive-ui';
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ArticleRepository } from '@/data/api';
import avaterUrl from '@/image/avater.jpg';
import { ArticleCategory } from '@/model/Article';
import { runCatching } from '@/pages/result';
import { doAction, useIsWideScreen } from '@/pages/util';

const route = useRoute();
const router = useRouter();
const isWideScreen = useIsWideScreen(850);
const message = useMessage();

const articleId = route.params.id as string | undefined;
let loaded = articleId === undefined;

const articleCategoryOptions = [
  { value: 'Guide', label: '使用指南' },
  { value: 'General', label: '小说交流' },
  { value: 'Support', label: '反馈与建议' },
];

const formRef = ref<FormInst>();
const formValue = ref({
  title: '',
  content: '',
  category: (route.query.category || 'General') as ArticleCategory,
});
const formRules: FormRules = {
  title: [
    {
      validator: (_rule: FormItemRule, value: string) =>
        value.trim().length >= 2,
      message: '标题长度不能少于2个字符',
      trigger: 'input',
    },
    {
      validator: (_rule: FormItemRule, value: string) => value.length <= 80,
      message: '标题长度不能超过80个字符',
      trigger: 'input',
    },
  ],
  content: [
    {
      validator: (_rule: FormItemRule, value: string) =>
        value.trim().length >= 2,
      message: '内容长度不能少于2个字符',
      trigger: 'input',
    },
    {
      validator: (_rule: FormItemRule, value: string) => value.length <= 20_000,
      message: '内容长度不能超过2万个字符',
      trigger: 'input',
    },
  ],
};

onMounted(async () => {
  if (articleId !== undefined) {
    const article = await runCatching(ArticleRepository.getArticle(articleId));
    if (article.ok) {
      formValue.value.title = article.value.title;
      formValue.value.category = article.value.category;
      formValue.value.content = article.value.content;
      loaded = true;
    } else {
      message.error('载入失败');
    }
  }
});

const submit = async () => {
  if (!loaded) {
    message.warning('小说未载入');
    return;
  }

  const validated = await new Promise<boolean>(function (resolve, _reject) {
    formRef.value?.validate((errors) => {
      if (errors) resolve(false);
      else resolve(true);
    });
  });
  if (!validated) return;

  if (articleId === undefined) {
    await doAction(
      ArticleRepository.createArticle(formValue.value).then((id) =>
        router.push({ path: `/forum/${id}` })
      ),
      '发布',
      message
    );
  } else {
    await doAction(
      ArticleRepository.updateArticle(articleId, formValue.value).then(() =>
        router.push({ path: `/forum/${articleId}` })
      ),
      '更新',
      message
    );
  }
};

const formatExample: [string, string][] = [
  ['段落之间要有空行', '第一段巴拉巴拉\n\n第二段巴拉巴拉'],
  ['粗体', '**随机文本**'],
  ['斜体', '*随机文本*'],
  ['删除线', '~~随机文本~~'],
  ['分隔线', '---'],
  ['列表', '- 第一项\n- 第二项\n- 第三项\n'],
  ['链接', '[链接名称](https://books.fishhawk.top)'],
  ['网络图片', `![](${avaterUrl})`],
  ['多级标题', '# 一级标题\n\n## 二级标题\n\n### 三级标题'],
];
</script>

<template>
  <div class="layout-content">
    <n-h1>{{ articleId === undefined ? '发布' : '编辑' }}文章</n-h1>
    <n-form
      ref="formRef"
      :model="formValue"
      :rules="formRules"
      :label-placement="isWideScreen ? 'left' : 'top'"
      label-width="auto"
      style="max-width: 800px"
    >
      <n-form-item-row path="title" label="标题">
        <n-input
          v-model:value="formValue.title"
          placeholder="标题"
          maxlength="80"
          show-count
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>
      <n-form-item-row path="category" label="版块">
        <c-radio-group
          v-model:value="formValue.category"
          :options="articleCategoryOptions"
        />
      </n-form-item-row>
      <n-form-item-row path="content" label="内容">
        <n-input
          v-model:value="formValue.content"
          type="textarea"
          placeholder="内容"
          :autosize="{
            minRows: 8,
            maxRows: 24,
          }"
          maxlength="20000"
          show-count
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>
    </n-form>

    <c-button
      label="提交"
      :icon="UploadOutlined"
      require-login
      size="large"
      type="primary"
      class="float"
      @action="submit"
    />

    <n-divider />

    <template v-if="formValue.content.trim()">
      <section-header title="预览" />
      <markdown :source="formValue.content" />
    </template>

    <section-header title="格式帮助" />
    <n-table :bordered="false">
      <thead>
        <tr>
          <th><b>格式</b></th>
          <th><b>原文</b></th>
          <th><b>预览</b></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="[name, code] of formatExample">
          <td>
            <b>{{ name }}</b>
          </td>
          <td style="white-space: pre-wrap">{{ code }}</td>
          <td>
            <Markdown :source="code" />
          </td>
        </tr>
      </tbody>
    </n-table>
  </div>
</template>
