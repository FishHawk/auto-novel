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
  <n-thing title="术语表">
    <template #description>
      术语表过大可能会使得翻译质量下降（例如：百度/有道将无法从判断人名性别，导致人称代词错误），请不要过度依赖术语表。
      <br />
      术语表修改后，再次更新翻译时，已翻译章节会重新翻译有变化的段落，尽量避免频繁编辑。
      <br />
      GPT暂不支持。
    </template>
    <n-p>
      <n-input-group style="max-width: 400px">
        <n-input
          pair
          v-model:value="termsToAdd"
          separator="=>"
          :placeholder="['日文', '中文']"
        />
        <n-button @click="addTerm()">添加</n-button>
        <AsyncButton :on-async-click="submit">提交</AsyncButton>
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
  </n-thing>
</template>
