<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { ApiSakura } from '@/data/api/api_sakura';
import { ApiWebNovel } from '@/data/api/api_web_novel';
import { useSettingStore } from '@/data/stores/setting';
import {
  buildWebTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { TranslatorId } from '@/data/translator';

import AdvanceOptions from './AdvanceOptions.vue';

const props = defineProps<{
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
  total: number;
  jp: number;
  baidu: number;
  youdao: number;
  gpt: number;
  sakura: number;
  glossary: { [key: string]: string };
}>();

const { providerId, novelId, titleJp, titleZh, total } = props;

const emit = defineEmits<{
  'update:jp': [number];
  'update:baidu': [number];
  'update:youdao': [number];
  'update:gpt': [number];
}>();

const setting = useSettingStore();
const message = useMessage();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();

const advanceOptions = ref<InstanceType<typeof AdvanceOptions>>();
const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (translatorId: 'baidu' | 'youdao') =>
  translateTask?.value?.startTask(
    { type: 'web', providerId, novelId },
    advanceOptions.value!!.getTranslationOptions(),
    { id: translatorId }
  );

const files = computed(() => {
  const title =
    setting.downloadFilenameType === 'jp' ? titleJp : titleZh ?? titleJp;

  const { mode, translationsMode, translations } = setting.downloadFormat;

  const type = setting.downloadFormat.type;

  let lang: 'zh' | 'zh-jp' | 'jp-zh';
  if (mode === 'jp' || mode === 'zh') {
    lang = 'zh';
  } else if (mode === 'mix') {
    lang = 'zh-jp';
  } else {
    lang = 'jp-zh';
  }

  return {
    jp: ApiWebNovel.createFileUrl({
      providerId,
      novelId,
      lang: 'jp',
      translationsMode,
      translations: [],
      type,
      title,
    }),
    zh: ApiWebNovel.createFileUrl({
      providerId,
      novelId,
      lang,
      translationsMode,
      translations,
      type,
      title,
    }),
  };
});

const submitGlossary = async () => {
  const result = await ApiWebNovel.updateGlossary(
    props.providerId,
    props.novelId,
    props.glossary
  );
  if (result.ok) {
    message.success('术语表提交成功');
  } else {
    message.error('术语表提交失败：' + result.error.message);
  }
};

const submitPublicSakuraJob = async () => {
  const options = advanceOptions.value!!.getTranslationOptions();
  const result = await ApiSakura.createSakuraJobWebTranslate(
    providerId,
    novelId,
    {
      start: options.startIndex,
      end: options.endIndex,
      expire: options.translateExpireChapter,
    }
  );
  if (result.ok) {
    message.info('排队成功');
  } else {
    message.error('排队失败:' + result.error.message);
  }
};

const submitJob = (id: 'gpt' | 'sakura') => {
  const options = advanceOptions.value!!.getTranslationOptions();
  const task = buildWebTranslateTask(providerId, novelId, {
    start: options.startIndex,
    end: options.endIndex,
    expire: options.translateExpireChapter,
  });
  const workspace = id === 'gpt' ? gptWorkspace : sakuraWorkspace;
  const success = workspace.addJob({
    task,
    description: titleJp,
    createAt: Date.now(),
  });
  if (success) {
    message.success('排队成功');
  } else {
    message.error('排队失败：翻译任务已经存在');
  }
};

const translatorLabels = computed(() => ({
  baidu: `百度(${props.baidu}/${total})`,
  youdao: `有道(${props.youdao}/${total})`,
  gpt: `GPT3(${props.gpt}/${total})`,
  sakura: `Sakura(${props.sakura}/${total})`,
}));
</script>

<template>
  <advance-options
    ref="advanceOptions"
    type="web"
    :glossary="glossary"
    :submit="submitGlossary"
  >
    <c-button
      label="下载机翻"
      tag="a"
      :href="files.zh.url"
      :download="files.zh.filename"
      target="_blank"
    />
    <c-button
      label="下载日文"
      tag="a"
      :href="files.jp.url"
      :download="files.jp.filename"
      target="_blank"
    />
  </advance-options>

  <n-list>
    <n-list-item
      v-for="translatorId of (['baidu', 'youdao', 'gpt', 'sakura'] as TranslatorId[])"
    >
      {{ translatorLabels[translatorId] }}
      <template #suffix>
        <c-button
          v-if="translatorId === 'baidu'"
          label="更新百度"
          size="small"
          tertiary
          @click="startTranslateTask(translatorId)"
        />
        <c-button
          v-if="translatorId === 'youdao'"
          label="更新有道"
          size="small"
          tertiary
          @click="startTranslateTask(translatorId)"
        />
        <c-button
          v-if="translatorId === 'gpt'"
          label="排队GPT"
          size="small"
          tertiary
          @click="() => submitJob(translatorId)"
        />
        <n-flex v-if="translatorId === 'sakura'" :wrap="false">
          <c-button
            label="公用排队"
            async
            require-login
            size="small"
            tertiary
            @click="submitPublicSakuraJob"
          />
          <c-button
            label="排队Sakura"
            size="small"
            tertiary
            @click="submitJob(translatorId)"
          />
        </n-flex>
      </template>
    </n-list-item>
  </n-list>

  <TranslateTask
    ref="translateTask"
    @update:jp="(zh) => emit('update:jp', zh)"
    @update:baidu="(zh) => emit('update:baidu', zh)"
    @update:youdao="(zh) => emit('update:youdao', zh)"
    @update:gpt="(zh) => emit('update:gpt', zh)"
    style="margin-top: 20px"
  />
</template>
