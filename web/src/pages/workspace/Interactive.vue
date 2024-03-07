<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref, watch } from 'vue';

import {
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import {
  Translator,
  TranslatorConfig,
  TranslatorId,
} from '@/data/translator/translator';

const message = useMessage();

const textJp = ref('');
const textZh = ref('');

const translatorId = ref<TranslatorId>('sakura');
const translationOptions: { label: string; value: TranslatorId }[] = [
  { label: '百度', value: 'baidu' },
  { label: '有道', value: 'youdao' },
  { label: 'GPT', value: 'gpt' },
  { label: 'Sakura', value: 'sakura' },
];

watch(textJp, () => {
  textZh.value = '';
});
watch(translatorId, () => {
  textZh.value = '';
});

const gptWorkspace = useGptWorkspaceStore();
const selectedGptWorkerId = ref(gptWorkspace.workers.at(0)?.id);

const sakuraWorkspace = useSakuraWorkspaceStore();
const selectedSakuraWorkerId = ref(sakuraWorkspace.workers.at(0)?.id);

interface SavedTranslation {
  id: TranslatorId;
  workerId?: string;
  endpoint?: string;
  jp: string;
  zh: string;
}
const savedTranslation = ref<SavedTranslation[]>([]);

const translate = async () => {
  let config: TranslatorConfig;
  let workerId: string | undefined;
  let endpoint: string | undefined;
  const id = translatorId.value;
  if (id === 'gpt') {
    const worker = gptWorkspace.workers.find(
      (it) => it.id === selectedGptWorkerId.value
    );
    if (worker === undefined) {
      message.error('未选择GPT翻译器');
      return;
    }
    const realEndpoint = (() => {
      if (worker.endpoint.length === 0) {
        if (worker.type === 'web') {
          return 'https://chat.openai.com/backend-api';
        } else {
          return 'https://api.openai.com';
        }
      } else {
        return worker.endpoint;
      }
    })();
    workerId = worker.id;
    endpoint = endpoint;
    config = {
      id,
      log: () => {},
      type: worker.type,
      model: worker.model ?? 'gpt-3.5',
      endpoint: realEndpoint,
      key: worker.key,
    };
  } else if (id === 'sakura') {
    const worker = sakuraWorkspace.workers.find(
      (it) => it.id === selectedSakuraWorkerId.value
    );
    if (worker === undefined) {
      message.error('未选择Sakura翻译器');
      return;
    }
    workerId = worker.id;
    endpoint = worker.endpoint;
    config = {
      id,
      log: () => {},
      endpoint: worker.endpoint,
      useLlamaApi: worker.useLlamaApi ?? false,
    };
  } else {
    config = {
      id,
      log: () => {},
    };
  }

  try {
    const translator = await Translator.create(config, false);
    const linesJp = textJp.value.split('\n');
    const linesZh = await translator.translate(linesJp, {});
    textZh.value = linesZh.join('\n');
  } catch (e: any) {
    message.error(`翻译器错误：${e}`);
  }

  savedTranslation.value.push({
    id,
    workerId,
    endpoint,
    jp: textJp.value,
    zh: textZh.value,
  });
};
const clearTranslation = () => {
  textJp.value = '';
  textZh.value = '';
};
const copyToClipboard = () => {
  navigator.clipboard.writeText(textZh.value);
  message.info('已经将翻译结果复制到剪切板');
};
const clearSavedTranslation = () => {
  savedTranslation.value = [];
};
</script>

<template>
  <div class="layout-content">
    <n-h1>交互翻译</n-h1>

    <n-flex vertical>
      <c-action-wrapper title="排序">
        <n-flex vertical>
          <c-radio-group
            v-model:value="translatorId"
            :options="translationOptions"
            size="small"
          />

          <n-radio-group
            v-if="translatorId === 'gpt'"
            v-model:value="selectedGptWorkerId"
          >
            <n-flex vertical>
              <n-radio
                v-for="worker of gptWorkspace.workers"
                :key="worker.id"
                :value="worker.id"
              >
                {{ worker.id }}
                <n-text depth="3">
                  {{ worker.model }}@{{
                    worker.endpoint ? worker.endpoint : 'default'
                  }}
                </n-text>
              </n-radio>
            </n-flex>
          </n-radio-group>

          <n-radio-group
            v-if="translatorId === 'sakura'"
            v-model:value="selectedSakuraWorkerId"
          >
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
        </n-flex>
      </c-action-wrapper>

      <c-action-wrapper title="排序">
        <n-flex style="margin-bottom: 16px">
          <n-button-group size="small">
            <c-button label="翻译" async :round="false" @click="translate" />
            <c-button label="清空" :round="false" @click="clearTranslation" />
          </n-button-group>
          <n-button-group size="small">
            <c-button
              label="复制到剪贴板"
              :round="false"
              @click="copyToClipboard"
            />
          </n-button-group>
        </n-flex>
      </c-action-wrapper>
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

    <section-header title="翻译历史">
      <c-button label="清空" @click="clearSavedTranslation" />
    </section-header>

    <n-empty v-if="savedTranslation.length === 0" description="没有翻译历史" />
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
            {{ t.workerId }}
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
