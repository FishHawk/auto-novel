<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed } from 'vue';
import { ref } from 'vue';

import { Epub } from '@/data/epub/epub';

const message = useMessage();

const showPreviewModal = ref(false);
const inputElRef = ref<HTMLInputElement>();

const katakanaThredhold = ref(10);
const katakanaCounter = computed(() => {
  const regexp = /[\u30A0-\u30FF]{2,}/g;
  const matches = content.value.matchAll(regexp);
  const katakanaCounter = new Map<string, number>();
  for (const match of matches) {
    const w = match[0];
    katakanaCounter.set(w, (katakanaCounter.get(w) || 0) + 1);
  }

  const sortedKatakanaCounter = new Map(
    [...katakanaCounter].sort(([_w1, c1], [_w2, c2]) => c2 - c1)
  );

  return sortedKatakanaCounter;
});
const katakanas = computed(() => {
  return new Map(
    [...katakanaCounter.value].filter(([w, c]) => c > katakanaThredhold.value)
  );
});

const openOpenFileDialog = () => inputElRef.value?.click();

const filename = ref<string | null>(null);
const content = ref<string>('');

const loadFile = () => {
  const selectedFile = inputElRef.value?.files?.[0];
  if (!selectedFile) {
    message.error('没有选中文件');
    return;
  }

  if (selectedFile.name.endsWith('.epub')) {
    const getFullTextFromEpubFile = async (file: File) => {
      const fullContent: string[] = [];
      await Epub.forEachXHtmlFile(file, (_path, doc) => {
        Array.from(doc.getElementsByClassName('rt')).forEach((node) =>
          node.parentNode!!.removeChild(node)
        );
        fullContent.push(doc.body.textContent ?? '');
      });
      return fullContent.join('\n');
    };
    getFullTextFromEpubFile(selectedFile)
      .then((text) => {
        filename.value = selectedFile?.name;
        content.value = text;
      })
      .catch((err) => {
        message.error('文件读取失败:' + err);
      });
  } else if (selectedFile.name.endsWith('.txt')) {
    const reader = new FileReader();
    reader.onload = (res) => {
      content.value = res.target?.result as string;
      filename.value = selectedFile?.name;
    };
    reader.onerror = (err) => {
      message.error('文件读取失败:' + err);
    };
    reader.readAsText(selectedFile);
  } else {
    message.error('文件类型不合法，只能选择TXT和EPUB文件');
    return;
  }
};

const copyResult = () => {
  const obj = Object.fromEntries(
    Array.from(katakanas.value).map(([key, value]) => [key, value.toString()])
  );
  const jsonString = JSON.stringify(obj, null, 2);
  navigator.clipboard.writeText(jsonString);
  message.info('已经将结果复制到剪切板');
};
</script>

<template>
  <div class="layout-content">
    <n-h1>片假名统计</n-h1>

    <n-flex align="center">
      <c-button label="打开文件" @click="openOpenFileDialog()" />
      <c-button v-if="filename" label="预览" @click="showPreviewModal = true" />
      <n-p>{{ filename }}</n-p>
      <input
        ref="inputElRef"
        type="file"
        accept=".txt,.epub"
        @change="loadFile"
        style="width: 0; height: 0"
      />
    </n-flex>

    <n-divider />

    <div style="display: flex">
      <div style="flex: 50%; padding-right: 50px">
        <n-p>
          片假名次数阈值
          <n-input-number v-model:value="katakanaThredhold" clearable />
        </n-p>
        <c-button label="复制结果" @click="copyResult()" />
      </div>
      <div style="flex: 50%">
        <n-card :title="`片假名统计结果(${katakanas.size}个)`">
          <n-scrollbar trigger="none" style="width: 300px; max-height: 400px">
            {
            <br />
            <template v-for="([word, number], index) in katakanas">
              {{ `"${word}": "${number}"` }}
              <template v-if="index != katakanas.size - 1">,</template>
              <br />
            </template>
            }
          </n-scrollbar>
        </n-card>
      </div>
    </div>
  </div>

  <c-modal title="预览（前100行）" v-model:show="showPreviewModal">
    <n-p v-for="line of content.split('\n').slice(0, 100)">
      {{ line }}
    </n-p>
  </c-modal>
</template>
