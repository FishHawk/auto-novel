<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules, useMessage } from 'naive-ui';
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ApiArticle } from '@/data/api/api_article';
import avaterUrl from '@/images/avater.jpg';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const articleId = route.params.id as string | undefined;

const formRef = ref<FormInst>();
const formValue = ref({
  title: '',
  content: '',
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
    const article = await ApiArticle.getArticle(articleId);
    if (article.ok) {
      formValue.value.title = article.value.title;
      formValue.value.content = article.value.content;
    } else {
      message.error('载入失败');
    }
  }
});

async function submit() {
  const validated = await new Promise<boolean>(function (resolve, _reject) {
    formRef.value?.validate((errors) => {
      if (errors) resolve(false);
      else resolve(true);
    });
  });
  if (!validated) return;

  if (articleId === undefined) {
    const result = await ApiArticle.createArticle({
      title: formValue.value.title,
      content: formValue.value.content,
    });
    if (result.ok) {
      message.info('发布成功');
      router.push({ path: `/forum/${result.value}` });
    } else {
      message.error('发布失败:' + result.error.message);
    }
  } else {
    const result = await ApiArticle.updateArticle(articleId, {
      title: formValue.value.title,
      content: formValue.value.content,
    });
    if (result.ok) {
      message.info('更新成功');
      router.push({ path: `/forum/${articleId}` });
    } else {
      message.error('更新失败:' + result.error.message);
    }
  }
}

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
      label-placement="left"
      label-width="auto"
      style="max-width: 800px"
    >
      <n-form-item-row path="title">
        <n-input
          v-model:value="formValue.title"
          placeholder="标题"
          maxlength="80"
          show-count
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>
      <n-form-item-row path="content">
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
    <AsyncButton type="primary" :on-async-click="submit">提交</AsyncButton>

    <n-divider />

    <section v-if="formValue.content.trim()">
      <SectionHeader title="预览" />
      <Markdown :source="formValue.content" />
    </section>

    <section>
      <SectionHeader title="格式帮助" />
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
    </section>
  </div>
</template>
