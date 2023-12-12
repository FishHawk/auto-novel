<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed, ref, watch } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { ApiSakura } from '@/data/api/api_sakura';
import { ApiWebNovel } from '@/data/api/api_web_novel';
import { useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';
import {
  buildWebTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { TranslatorId } from '@/data/translator/translator';
import { getTranslatorLabel, useIsDesktop } from '@/data/util';

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
const isDesktop = useIsDesktop(600);
const message = useMessage();
const userData = useUserDataStore();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();

const startIndex = ref<number | null>(0);
const endIndex = ref<number | null>(65536);

const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (translatorId: 'baidu' | 'youdao') =>
  translateTask?.value?.startTask(
    { type: 'web', providerId, novelId },
    {
      translateExpireChapter: translateExpireChapter.value,
      syncFromProvider: syncFromProvider.value,
      startIndex: startIndex.value ?? 0,
      endIndex: endIndex.value ?? 65536,
    },
    { id: translatorId }
  );

const files = computed<
  {
    label: string;
    translatorId?: TranslatorId;
    files: { label: string; url: string; filename: string }[];
  }[]
>(() => {
  const title =
    setting.downloadFilenameType === 'jp' ? titleJp : titleZh ?? titleJp;

  const createFile = (
    label: string,
    lang:
      | 'jp'
      | 'zh-baidu'
      | 'zh-youdao'
      | 'zh-gpt'
      | 'zh-sakura'
      | 'mix-baidu'
      | 'mix-youdao'
      | 'mix-gpt'
      | 'mix-sakura',
    type: 'epub' | 'txt'
  ) => {
    const { url, filename } = ApiWebNovel.createFileUrl({
      providerId,
      novelId,
      lang,
      type,
      title,
    });
    return {
      label,
      url,
      filename,
    };
  };

  return [
    {
      label: `日文(${props.jp}/${total})`,
      files: [createFile('TXT', 'jp', 'txt'), createFile('EPUB', 'jp', 'epub')],
    },
    {
      label: `百度(${props.baidu}/${total})`,
      translatorId: 'baidu',
      files: [
        createFile('TXT', 'zh-baidu', 'txt'),
        createFile('中日对比TXT', 'mix-baidu', 'txt'),
        createFile('EPUB', 'zh-baidu', 'epub'),
        createFile('中日对比EPUB', 'mix-baidu', 'epub'),
      ],
    },
    {
      label: `有道(${props.youdao}/${total})`,
      translatorId: 'youdao',
      files: [
        createFile('TXT', 'zh-youdao', 'txt'),
        createFile('中日对比TXT', 'mix-youdao', 'txt'),
        createFile('EPUB', 'zh-youdao', 'epub'),
        createFile('中日对比EPUB', 'mix-youdao', 'epub'),
      ],
    },
    {
      label: `GPT3(${props.gpt}/${total})`,
      translatorId: 'gpt',
      files: [
        createFile('TXT', 'zh-gpt', 'txt'),
        createFile('中日对比TXT', 'mix-gpt', 'txt'),
        createFile('EPUB', 'zh-gpt', 'epub'),
        createFile('中日对比EPUB', 'mix-gpt', 'epub'),
      ],
    },
    {
      label: `Sakura(${props.sakura}/${total})`,
      translatorId: 'sakura',
      files: [
        createFile('TXT', 'zh-sakura', 'txt'),
        createFile('中日对比TXT', 'mix-sakura', 'txt'),
        createFile('EPUB', 'zh-sakura', 'epub'),
        createFile('中日对比EPUB', 'mix-sakura', 'epub'),
      ],
    },
  ];
});

const showTranslateOptions = ref(false);

const tryUseChineseTitleAsFilename = ref(setting.downloadFilenameType === 'zh');
const translateExpireChapter = ref(false);
const syncFromProvider = ref(false);
watch(
  tryUseChineseTitleAsFilename,
  (it) => (setting.downloadFilenameType = it ? 'zh' : 'jp')
);

async function submitGlossary() {
  const result = await ApiWebNovel.updateGlossary(
    providerId,
    novelId,
    props.glossary
  );
  if (result.ok) {
    message.success('术语表提交成功');
  } else {
    message.error('术语表提交失败：' + result.error.message);
  }
}

async function submitPublicSakuraJob() {
  const result = await ApiSakura.createSakuraJobWebTranslate(
    providerId,
    novelId,
    {
      start: startIndex.value ?? 0,
      end: endIndex.value ?? 65535,
      expire: translateExpireChapter.value,
    }
  );
  if (result.ok) {
    message.info('排队成功');
  } else {
    message.error('排队失败:' + result.error.message);
  }
}

const submitJob = (id: 'gpt' | 'sakura') => {
  if (id === 'sakura' && !userData.isTrusted) {
    message.error('目前普通用户无法使用Sakura工作区翻译网络小说');
    return;
  }

  const task = buildWebTranslateTask(providerId, novelId, {
    start: startIndex.value ?? 0,
    end: endIndex.value ?? 65535,
    expire: translateExpireChapter.value,
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
</script>

<template>
  <n-p depth="3" style="font-size: 12px">
    # 翻译功能需要需要安装浏览器插件，参见
    <RouterNA to="/forum/64f3d63f794cbb1321145c07">插件使用说明</RouterNA>
  </n-p>
  <workspace-nav />
  <n-button
    @click="showTranslateOptions = !showTranslateOptions"
    style="margin-bottom: 8px"
  >
    翻译设置
  </n-button>

  <n-collapse-transition
    :show="showTranslateOptions"
    style="margin-bottom: 16px"
  >
    <n-list bordered>
      <n-list-item>
        <AdvanceOptionSwitch
          title="中文文件名"
          description="如果小说标题已经被翻译，则使用翻译后的中文标题作为下载的文件名。"
          v-model:value="tryUseChineseTitleAsFilename"
        />
      </n-list-item>

      <n-list-item>
        <AdvanceOptionSwitch
          title="翻译过期章节"
          description="在启动翻译任务时，重新翻译术语表过期的章节。一次性设定，默认关闭。"
          v-model:value="translateExpireChapter"
        />
      </n-list-item>

      <n-list-item v-if="userData.passWeek">
        <AdvanceOptionSwitch
          title="与源站同步"
          description="在启动翻译任务时，同步已缓存章节。如果缓存章节与源站不匹配，会删除章节，包含现有的翻译。慎用，不要抱着试试的心情用这个功能，用之前请确保你知道自己在干什么。一次性设定，默认关闭。"
          v-model:value="syncFromProvider"
        />
      </n-list-item>

      <n-list-item>
        <AdvanceOption
          title="自定义更新范围"
          description="控制翻译任务的范围，章节序号可以看下面目录结尾方括号里的数字。比如，从0到10，表示章节需要属于区间[0，10)的章节，不包含序号10。"
        >
          <n-input-group style="margin-top: 4px">
            <n-input-group-label size="small">从</n-input-group-label>
            <n-input-number
              size="small"
              v-model:value="startIndex"
              :min="0"
              style="width: 100px"
            />
            <n-input-group-label size="small">到</n-input-group-label>
            <n-input-number
              size="small"
              v-model:value="endIndex"
              :min="0"
              style="width: 100px"
            />
          </n-input-group>
        </AdvanceOption>
      </n-list-item>

      <n-list-item>
        <AdvanceOption
          title="术语表"
          description="术语表过大可能会使得翻译质量下降，此外，出于安全起见，Sakura只会使用日语长度超过两个字的术语。"
        >
          <GlossaryEdit :glossary="glossary" :submit="submitGlossary" />
        </AdvanceOption>
      </n-list-item>
    </n-list>
  </n-collapse-transition>

  <n-list style="background-color: #0000">
    <n-list-item v-for="row in files">
      <template #suffix>
        <template v-if="row.translatorId === 'sakura'">
          <n-space :wrap="false">
            <async-button
              tertiary
              size="small"
              @async-click="submitPublicSakuraJob"
            >
              公用排队
            </async-button>
            <n-button tertiary size="small" @click="() => submitJob('sakura')">
              Sakura排队
            </n-button>
          </n-space>
        </template>
        <template v-else-if="row.translatorId === 'gpt'">
          <n-button tertiary size="small" @click="() => submitJob('gpt')">
            GPT排队
          </n-button>
        </template>
        <n-button
          v-else-if="row.translatorId"
          tertiary
          size="small"
          @click="startTranslateTask(row.translatorId)"
        >
          {{ getTranslatorLabel(row.translatorId) }}翻译
        </n-button>
      </template>
      <n-space :vertical="!isDesktop">
        <n-text>{{ row.label }}</n-text>
        <n-space>
          <n-a
            v-for="file in row.files"
            :href="file.url"
            :download="file.filename"
            target="_blank"
          >
            {{ file.label }}
          </n-a>
        </n-space>
      </n-space>
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
