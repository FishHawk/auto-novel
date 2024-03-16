<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

const { glossary } = defineProps<{
  glossary: { [key: string]: string };
}>();

const message = useMessage();

const importGlossaryRaw = ref('');
const termsToAdd = ref<[string, string]>(['', '']);

const deleteTerm = (jp: string) => {
  delete glossary[jp];
};

const addTerm = () => {
  const [jp, zh] = termsToAdd.value;
  if (jp && zh) {
    glossary[jp] = zh;
    termsToAdd.value = ['', ''];
  }
};

const exportGlossary = () => {
  navigator.clipboard.writeText(JSON.stringify(glossary));
  message.info('已经将术语表复制到剪切板');
};

const importGlossary = () => {
  const inputGlossary = (() => {
    try {
      const obj = JSON.parse(importGlossaryRaw.value);
      if (typeof obj !== 'object') return null;
      const inputGlossary: { [key: string]: string } = {};
      for (const jp in obj) {
        const zh = obj[jp];
        if (typeof zh !== 'string') return null;
        inputGlossary[jp] = zh;
      }
      return inputGlossary;
    } catch {
      return null;
    }
  })();
  if (inputGlossary === null) {
    message.error('导入的术语表格式不正确');
  } else {
    for (const jp in inputGlossary) {
      const zh = inputGlossary[jp];
      glossary[jp] = zh;
    }
  }
};
</script>

<template>
  <n-flex vertical size="large" style="max-width: 400px">
    <n-input-group>
      <n-input
        pair
        v-model:value="termsToAdd"
        size="small"
        separator="=>"
        :placeholder="['日文', '中文']"
        :input-props="{ spellcheck: false }"
      />
      <c-button label="添加" :round="false" size="small" @action="addTerm" />
    </n-input-group>

    <n-input-group>
      <n-input
        v-model:value="importGlossaryRaw"
        size="small"
        placeholder="批量导入术语表"
        :input-props="{ spellcheck: false }"
      />
      <c-button
        label="导出"
        :round="false"
        size="small"
        @action="exportGlossary"
      />
      <c-button
        label="导入"
        :round="false"
        size="small"
        @action="importGlossary"
      />
    </n-input-group>

    <table style="border-spacing: 16px 0px; font-size: 12px">
      <tr v-for="workJp in Object.keys(glossary).reverse()">
        <td>{{ workJp }}</td>
        <td nowrap="nowrap">=></td>
        <td>{{ glossary[workJp] }}</td>
        <td>
          <c-button
            label="删除"
            secondary
            type="error"
            size="tiny"
            @action="deleteTerm(workJp)"
          />
        </td>
      </tr>
    </table>
  </n-flex>
</template>
