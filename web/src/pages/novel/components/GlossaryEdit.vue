<script lang="ts" setup>
import { ref } from 'vue';

const { glossary } = defineProps<{
  glossary: { [key: string]: string };
  submit: () => Promise<void>;
}>();

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
</script>

<template>
  <n-p>
    <n-input-group style="max-width: 400px">
      <n-input
        pair
        v-model:value="termsToAdd"
        size="small"
        separator="=>"
        :placeholder="['日文', '中文']"
        :input-props="{ spellcheck: false }"
      />
      <n-button size="small" @click="addTerm()">添加</n-button>
      <AsyncButton size="small" :on-async-click="submit">提交</AsyncButton>
    </n-input-group>
  </n-p>

  <n-scrollbar style="max-height: 400px">
    <table style="border-spacing: 16px 0px">
      <tr v-for="(termZh, termJp) in glossary">
        <td>{{ termJp }}</td>
        <td>=></td>
        <td>{{ termZh }}</td>
        <td>
          <n-button size="tiny" @click="deleteTerm(termJp as string)">
            删除
          </n-button>
        </td>
      </tr>
    </table>
  </n-scrollbar>
</template>
