<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

const { glossary } = defineProps<{
  glossary: { [key: string]: string };
}>();

const message = useMessage();

const importGlossaryRaw = ref('');
const termsToAdd = ref<[string, string]>(['', '']);

function deleteTerm(jp: string) {
  delete glossary[jp];
}

function addTerm() {
  const [jp, zh] = termsToAdd.value;
  if (jp && zh) {
    glossary[jp] = zh;
    termsToAdd.value = ['', ''];
  }
}

function exportGlossary() {
  navigator.clipboard.writeText(JSON.stringify(glossary));
  message.info('已经将术语表复制到剪切板');
}

function importGlossary() {
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
}
</script>

<template>
  <div style="max-width: 400px">
    <n-p>
      <n-input-group>
        <n-input
          v-model:value="importGlossaryRaw"
          size="small"
          placeholder="批量导入术语表"
          :input-props="{ spellcheck: false }"
        />
        <n-button size="small" @click="exportGlossary()">导出</n-button>
        <n-button size="small" @click="importGlossary()">导入</n-button>
      </n-input-group>
    </n-p>

    <n-p>
      <n-input-group>
        <n-input
          pair
          v-model:value="termsToAdd"
          size="small"
          separator="=>"
          :placeholder="['日文', '中文']"
          :input-props="{ spellcheck: false }"
        />
        <n-button size="small" @click="addTerm()">添加</n-button>
      </n-input-group>
    </n-p>

    <n-p>
      <n-scrollbar style="max-height: 400px">
        <table style="border-spacing: 16px 0px; font-size: 12px">
          <tr v-for="(termZh, termJp) in glossary">
            <td>{{ termJp }}</td>
            <td nowrap="nowrap">=></td>
            <td>{{ termZh }}</td>
            <td>
              <n-button size="tiny" @click="deleteTerm(termJp as string)">
                删除
              </n-button>
            </td>
          </tr>
        </table>
      </n-scrollbar>
    </n-p>
  </div>
</template>
