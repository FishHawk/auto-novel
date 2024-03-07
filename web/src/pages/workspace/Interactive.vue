<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref, watch } from 'vue';

import { useSakuraWorkspaceStore } from '@/data/stores/workspace';
import { Translator, TranslatorConfig } from '@/data/translator/translator';

const message = useMessage();

const textJp = ref('');
const textZh = ref('');

watch(textJp, () => {
  textZh.value = '';
});

const sakuraWorkspace = useSakuraWorkspaceStore();
const selectedSakuraWorkerId = ref(sakuraWorkspace.workers.at(0)?.id);

interface SavedTranslation {
  id: string;
  endpoint: string;
  jp: string;
  zh: string;
}
const savedTranslation = ref<SavedTranslation[]>([]);

const translate = async () => {
  const worker = sakuraWorkspace.workers.find(
    (it) => it.id === selectedSakuraWorkerId.value
  );
  if (worker === undefined) {
    message.error('未选择Sakura翻译器');
    return;
  }
  const config: TranslatorConfig = {
    id: 'sakura',
    log: () => {},
    endpoint: worker.endpoint,
    useLlamaApi: worker.useLlamaApi ?? false,
  };

  try {
    const translator = await Translator.create(config, false);
    const linesJp = textJp.value.split('\n');
    const linesZh = await translator.translate(linesJp, {});
    textZh.value = linesZh.join('\n');
  } catch (e: any) {
    message.error(`翻译器错误：${e}`);
  }
};
const clearTranslation = () => {
  textJp.value = '';
  textZh.value = '';
};
const copyToClipboard = () => {
  navigator.clipboard.writeText(textZh.value);
  message.info('已经将翻译结果复制到剪切板');
};
const save = () => {
  const worker = sakuraWorkspace.workers.find(
    (it) => it.id === selectedSakuraWorkerId.value
  );
  if (worker === undefined) {
    message.error('未选择Sakura翻译器');
    return;
  }
  savedTranslation.value.push({
    id: worker.id,
    endpoint: worker.endpoint,
    jp: textJp.value,
    zh: textZh.value,
  });
};
const clearSavedTranslation = () => {
  savedTranslation.value = [];
};
</script>

<template>
  <div class="layout-content">
    <n-h1>交互翻译</n-h1>

    <n-p>
      <n-radio-group v-model:value="selectedSakuraWorkerId">
        <n-flex vertical>
          <n-radio
            v-for="worker of sakuraWorkspace.workers"
            :key="worker.id"
            :value="worker.id"
          >
            {{ worker.id }}
            <n-text depth="3">
              {{ worker.endpoint }}
            </n-text>
          </n-radio>
        </n-flex>
      </n-radio-group>
    </n-p>

    <n-flex style="margin-bottom: 16px">
      <n-button-group>
        <c-button label="翻译" async @click="translate" />
        <c-button label="清空" @click="clearTranslation" />
      </n-button-group>
      <n-button-group>
        <c-button label="复制到剪贴板" @click="copyToClipboard" />
        <c-button label="暂存" @click="save" />
      </n-button-group>
    </n-flex>

    <n-input-group>
      <n-input
        v-model:value="textJp"
        placeholder="输入需要翻译的文本"
        type="textarea"
        :autosize="{ minRows: 15 }"
        show-count
        :maxlength="5000"
        style="flex: 1"
        :input-props="{ spellcheck: false }"
      />

      <n-input
        v-model:value="textZh"
        readonly
        placeholder="翻译结果"
        type="textarea"
        :autosize="{ minRows: 15 }"
        show-count
        style="flex: 1"
        :input-props="{ spellcheck: false }"
      />
    </n-input-group>

    <section-header title="暂存">
      <c-button label="清空" @click="clearSavedTranslation" />
    </section-header>

    <n-empty v-if="savedTranslation.length === 0" description="没有暂存翻译" />
    <n-list>
      <n-list-item v-for="t of savedTranslation">
        <n-thing content-indented>
          <template #avatar>
            <n-icon-wrapper
              :size="12"
              :border-radius="0"
              style="margin-top: 5px"
            />
          </template>

          <template #header>
            {{ t.id }}
            <n-text depth="3" style="font-size: 12px; padding-left: 2px">
              {{ t.endpoint }}
            </n-text>
          </template>

          <template #description>
            <n-collapse style="margin-top: 16px">
              <n-collapse-item title="日文">
                <template v-for="line of t.jp.split('\n')">
                  {{ line }}
                  <br />
                </template>
              </n-collapse-item>
              <n-collapse-item title="中文">
                <template v-for="line of t.zh.split('\n')">
                  {{ line }}
                  <br />
                </template>
              </n-collapse-item>
            </n-collapse>
          </template>
        </n-thing>
      </n-list-item>
    </n-list>
  </div>
</template>
