<script lang="ts" setup>
import { useSettingStore } from '@/data/stores/setting';
import {
  buildPersonalTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { MoreVertFilled, PlusOutlined } from '@vicons/material';
import {
  UploadCustomRequestOptions,
  UploadFileInfo,
  useMessage,
} from 'naive-ui';
import { computed, ref, toRaw } from 'vue';

const props = defineProps<{ type: 'gpt' | 'sakura' }>();

const message = useMessage();
const setting = useSettingStore();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();

const volumes = ref<Volume[]>();

interface Volume {
  volumeId: string;
  createAt: number;
  total: number;
  finished: number;
  expired: number;
  glossary: { [key: string]: string };
}

const loadVolumes = () => {
  import('@/data/translator')
    .then((it) => it.PersonalVolumesManager.listVolumes())
    .then((rawVolumes) => {
      volumes.value = rawVolumes.map((rawVolume) => {
        return {
          volumeId: rawVolume.id,
          createAt: rawVolume.createAt,
          total: rawVolume.toc.length,
          finished: rawVolume.toc.filter((it) => {
            let chapterGlossaryId: string | undefined;
            if (props.type === 'gpt') {
              chapterGlossaryId = it.gpt;
            } else {
              chapterGlossaryId = it.sakura;
            }
            return chapterGlossaryId === rawVolume.glossaryId;
          }).length,
          expired: rawVolume.toc.filter((it) => {
            let chapterGlossaryId: string | undefined;
            if (props.type === 'gpt') {
              chapterGlossaryId = it.gpt;
            } else {
              chapterGlossaryId = it.sakura;
            }
            return (
              chapterGlossaryId !== undefined &&
              chapterGlossaryId !== rawVolume.glossaryId
            );
          }).length,
          glossary: rawVolume.glossary,
        };
      });
    });
};
loadVolumes();

const beforeUpload = ({ file }: { file: UploadFileInfo }) => {
  if (!(file.name.endsWith('.txt') || file.name.endsWith('.epub'))) {
    message.error(
      `上传失败:不允许的文件类型，必须是EPUB或TXT文件\n文件名:${file.file}`
    );
    return false;
  }
  if (file.file?.size && file.file.size > 1024 * 1024 * 40) {
    message.error(`上传失败:文件大小不能超过40MB\n文件名:${file.file}`);
    return false;
  }
};

const customRequest = ({
  file,
  onFinish,
  onError,
}: UploadCustomRequestOptions) => {
  import('@/data/translator')
    .then((it) => it.PersonalVolumesManager.saveVolume(file.file!!))
    .then(() => queueVolume(file.file!!.name))
    .then(onFinish)
    .catch((error) => {
      message.error(`上传失败:${error}\n文件名:${file.file}`);
      onError();
    });
};

const options = [
  {
    label: '全部排队',
    key: '全部排队',
  },
  {
    label: '清空文件',
    key: '清空文件',
  },
];
const handleSelect = (key: string) => {
  if (key === '全部排队') {
    queueAllVolumes();
  } else {
    showClearModal.value = true;
  }
};

const queueAllVolumes = () => {
  volumes.value?.forEach((volume) => {
    queueVolume(volume.volumeId);
  });
};

const showClearModal = ref(false);
const deleteAllVolumes = () =>
  import('@/data/translator')
    .then((it) => it.PersonalVolumesManager.deleteVolumesDb())
    .then(loadVolumes)
    .then(() => (showClearModal.value = false))
    .catch((error) => {
      message.error(`清空失败:${error}`);
    });

const modeOptions = [
  { value: 'zh', label: '中文' },
  { value: 'mix', label: '中日' },
  { value: 'mix-reverse', label: '日中' },
];

const order = ref('byId');
const orderOptions = [
  { value: 'byId', label: '按文件名排序' },
  { value: 'byCreateAt', label: '按添加时间排序' },
];

const sortedVolumes = computed(() => {
  if (order.value === 'byId') {
    return volumes.value?.sort((a, b) => a.volumeId.localeCompare(b.volumeId));
  } else {
    return volumes.value?.sort((a, b) => b.createAt - a.createAt);
  }
});

const queueVolume = (volumeId: string) => {
  const task = buildPersonalTranslateTask(volumeId, {
    start: 0,
    end: 65535,
    expire: true,
  });

  const addJob =
    props.type === 'gpt' ? gptWorkspace.addJob : sakuraWorkspace.addJob;

  const success = addJob({
    task,
    description: volumeId,
    createAt: Date.now(),
  });

  if (success) {
    message.success('排队成功');
  } else {
    message.error('排队失败：翻译任务已经存在');
  }
};

const downloadVolume = async (volumeId: string) => {
  const { mode } = setting.downloadFormat;

  let lang: 'zh' | 'zh-jp' | 'jp-zh';
  if (mode === 'jp' || mode === 'zh') {
    lang = 'zh';
  } else if (mode === 'mix') {
    lang = 'zh-jp';
  } else {
    lang = 'jp-zh';
  }

  try {
    const { filename, blob } = await import('@/data/translator').then((it) =>
      it.PersonalVolumesManager.makeTranslationVolumeFile({
        volumeId,
        lang,
        translationsMode: 'priority',
        translations: [props.type],
      })
    );

    const el = document.createElement('a');
    el.href = URL.createObjectURL(blob);
    el.target = '_blank';
    el.download = filename;
    el.click();
  } catch (error) {
    message.error(`文件生成错误：${error}`);
  }
};

const showGlossaryModal = ref(false);
const selectedVolumeToEditGlossary = ref<Volume>();
const submitGlossary = (
  volumeId: string,
  glossary: { [key: string]: string }
) =>
  import('@/data/translator')
    .then((it) =>
      it.PersonalVolumesManager.updateGlossary(volumeId, toRaw(glossary))
    )
    .then(() => message.success('术语表提交成功'))
    .catch((error) => message.error(`术语表提交失败：${error}`))
    .then(() => {});

const deleteVolume = (volumeId: string) =>
  import('@/data/translator')
    .then((it) => it.PersonalVolumesManager.deleteVolume(volumeId))
    .then(() => message.info('删除成功'))
    .then(() => loadVolumes())
    .catch((error) => message.error(`删除失败：${error}`));
</script>

<template>
  <n-flex vertical :size="0" v-bind="$attrs">
    <section-header title="文件列表">
      <n-flex :wrap="false">
        <n-upload
          multiple
          directory-dnd
          :show-file-list="false"
          :custom-request="customRequest"
          @finish="loadVolumes"
          @before-upload="beforeUpload"
        >
          <c-button label="添加文件" :icon="PlusOutlined" />
        </n-upload>
        <n-dropdown trigger="click" :options="options" @select="handleSelect">
          <n-button circle>
            <n-icon :component="MoreVertFilled" />
          </n-button>
        </n-dropdown>
      </n-flex>
    </section-header>

    <n-flex :wrap="false">
      <n-select
        v-model:value="setting.downloadFormat.mode"
        :options="modeOptions"
      />
      <n-select v-model:value="order" :options="orderOptions" />
    </n-flex>

    <n-divider style="margin-bottom: 4px" />

    <n-spin v-if="sortedVolumes === undefined" style="margin-top: 20px" />

    <n-empty
      v-else-if="sortedVolumes.length === 0"
      description="没有文件"
      style="margin-top: 20px"
    />

    <n-scrollbar v-else trigger="none" :size="24" style="flex: auto">
      <n-list style="padding-bottom: 48px">
        <n-list-item v-for="volume of sortedVolumes ?? []">
          {{ volume.volumeId }}
          <br />
          <n-text depth="3">
            <n-time :time="volume.createAt" type="relative" /> / 总计{{
              volume.total
            }}
            / 完成{{ volume.finished }} / 过期{{ volume.expired }}
          </n-text>
          <br />
          <n-flex style="margin-top: 4px">
            <n-button
              text
              type="primary"
              @click="() => queueVolume(volume.volumeId)"
            >
              排队
            </n-button>

            <n-button
              text
              type="primary"
              @click="() => downloadVolume(volume.volumeId)"
            >
              下载
            </n-button>

            <n-button
              text
              type="primary"
              @click="
                () => {
                  selectedVolumeToEditGlossary = volume;
                  showGlossaryModal = true;
                }
              "
            >
              编辑术语表
            </n-button>

            <n-popconfirm
              :show-icon="false"
              @positive-click="deleteVolume(volume.volumeId)"
              :negative-text="null"
            >
              <template #trigger>
                <n-button text type="error"> 删除 </n-button>
              </template>
              确定删除{{ volume.volumeId }}吗？
            </n-popconfirm>
          </n-flex>
        </n-list-item>
      </n-list>
    </n-scrollbar>
  </n-flex>

  <c-modal title="编辑术语表" v-model:show="showGlossaryModal">
    <n-p>{{ selectedVolumeToEditGlossary?.volumeId }} </n-p>
    <glossary-edit
      v-if="selectedVolumeToEditGlossary !== undefined"
      :glossary="selectedVolumeToEditGlossary.glossary"
    />
    <template #action>
      <n-button
        type="primary"
        @click="
        () =>
          submitGlossary(
            selectedVolumeToEditGlossary!!.volumeId,
            selectedVolumeToEditGlossary!!.glossary
          )
      "
      >
        提交
      </n-button>
      <n-button @click="showGlossaryModal = false"> 取消 </n-button>
    </template>
  </c-modal>

  <c-modal title="清空所有文件" v-model:show="showClearModal">
    <n-p>
      这将清空你的浏览器里面保存的所有EPUB/TXT文件，包括已经翻译的章节和术语表，无法恢复。
      你确定吗？
    </n-p>

    <template #action>
      <c-button label="确定" async type="primary" @click="deleteAllVolumes" />
    </template>
  </c-modal>
</template>
