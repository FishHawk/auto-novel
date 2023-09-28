<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed } from 'vue';
import { ref } from 'vue';

const message = useMessage();

const showPreviewModal = ref(false);
const inputElRef = ref<HTMLInputElement | null>(null);

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

function openOpenFileDialog() {
  inputElRef.value?.click();
}

const filename = ref<string | null>(null);
const content = ref<string>('');

function loadTxtFile() {
  const selectedFile = inputElRef.value?.files?.[0];
  if (!selectedFile) {
    message.error('没有选中文件');
  } else {
    const reader = new FileReader();
    reader.onload = (res) => {
      content.value = res.target?.result as string;
      filename.value = selectedFile?.name;
    };
    reader.onerror = (err) => {
      message.error('文件读取失败:' + err);
    };
    reader.readAsText(selectedFile);
  }
}
</script>

<template>
  <MainLayout>
    <n-h1>TXT工具箱</n-h1>

    <n-space align="center">
      <n-button @click="openOpenFileDialog()"> 打开TXT文件 </n-button>
      <n-button v-if="filename" @click="showPreviewModal = true">预览</n-button>
      <n-p>{{ filename }}</n-p>
      <input
        ref="inputElRef"
        type="file"
        accept=".txt"
        @change="loadTxtFile"
        style="width: 0; height: 0"
      />
    </n-space>

    <n-divider />

    <div style="display: flex">
      <div style="flex: 50%; padding-right: 50px">
        <n-p>
          片假名次数阈值
          <n-input-number v-model:value="katakanaThredhold" clearable />
        </n-p>
      </div>
      <div style="flex: 50%">
        <n-card :title="`片假名统计结果(${katakanas.size}个)`">
          <n-scrollbar trigger="none" style="width: 300px; max-height: 400px">
            <template v-for="[word, number] in katakanas">
              {{ `${word} : ${number}` }}
              <br />
            </template>
          </n-scrollbar>
        </n-card>
      </div>
    </div>

    <!--

    with open(file_path+'.统计.txt', 'w', encoding=ENCODING) as output_file:
        for katakana, count in sorted_katakana:
            if count >= MIN_OCCURRENCE:
                output_file.write("\n"+f"{count}次：")
                output_file.write(katakana) -->

    <n-modal v-model:show="showPreviewModal">
      <n-card
        style="width: min(600px, calc(100% - 16px))"
        :bordered="false"
        size="large"
        role="dialog"
        aria-modal="true"
        title="预览（前100行）"
      >
        <n-scrollbar trigger="none" style="max-height: 400px">
          <n-p v-for="line of content.slice(0, 100)">
            {{ line }}
          </n-p>
        </n-scrollbar>
      </n-card>
    </n-modal>
  </MainLayout>
</template>
