<script lang="ts" setup>
import { MoreVertOutlined } from '@vicons/material';
import { UploadFileInfo } from 'naive-ui';

import { Locator } from '@/data';
import { LocalVolumeMetadata } from '@/model/LocalVolume';
import { PlusOutlined } from '@vicons/material';
import { UploadCustomRequestOptions } from 'naive-ui';

const props = defineProps<{
  dropzone?: boolean;
  hideTitle?: boolean;
  options?: { [key: string]: (volumes: LocalVolumeMetadata[]) => void };
  filter?: (volume: LocalVolumeMetadata) => boolean;
  beforeVolumeAdd?: (file: File) => void;
}>();

const message = useMessage();

const volumes = ref<LocalVolumeMetadata[]>();

const loadVolumes = async () => {
  const repo = await Locator.localVolumeRepository();
  volumes.value = await repo.listVolume();
};
loadVolumes();

const options = computed(() => {
  const options =
    props.options === undefined
      ? []
      : Object.keys(props.options).map((it) => ({
          label: it,
          key: it,
        }));
  options.push({ label: '清空文件', key: '清空文件' });
  return options;
});
const handleSelect = (key: string) => {
  if (key === '清空文件') {
    showClearModal.value = true;
  } else {
    props.options?.[key]?.(volumes.value ?? []);
  }
};

const showClearModal = ref(false);
const deleteAllVolumes = () =>
  Locator.localVolumeRepository()
    .then((repo) => repo.deleteVolumesDb())
    .then(loadVolumes)
    .then(() => (showClearModal.value = false))
    .catch((error) => {
      message.error(`清空失败:${error}`);
    });

const order = ref<'byCreateAt' | 'byId'>('byCreateAt');
const orderOptions = [
  { value: 'byCreateAt', label: '按添加时间' },
  { value: 'byId', label: '按文件名' },
];
const sortedVolumes = computed(() => {
  const filteredVolumes =
    props.filter === undefined
      ? volumes.value
      : volumes.value?.filter(props.filter);
  if (order.value === 'byId') {
    return filteredVolumes?.sort((a, b) => a.id.localeCompare(b.id));
  } else {
    return filteredVolumes?.sort((a, b) => b.createAt - a.createAt);
  }
});

const deleteVolume = (volumeId: string) =>
  Locator.localVolumeRepository()
    .then((repo) => repo.deleteVolume(volumeId))
    .then(() => message.info('删除成功'))
    .then(() => loadVolumes())
    .catch((error) => message.error(`删除失败：${error}`));

defineExpose({ deleteVolume });

// Add volume
const onFinish = ({ file }: { file: UploadFileInfo }) => {
  if (props.beforeVolumeAdd) {
    props.beforeVolumeAdd(file.file!!);
  }
  loadVolumes();
};

const beforeUpload = ({ file }: { file: UploadFileInfo }) => {
  if (
    !(
      file.name.endsWith('.txt') ||
      file.name.endsWith('.srt') ||
      file.name.endsWith('.epub')
    )
  ) {
    message.error(`上传失败:文件类型不允许\n文件名： ${file.name}`);
    return false;
  }
  if (file.file?.size && file.file.size > 1024 * 1024 * 40) {
    message.error(`上传失败:文件大小不能超过40MB\n文件名: ${file.name}`);
    return false;
  }
};

const customRequest = ({
  file,
  onFinish,
  onError,
}: UploadCustomRequestOptions) => {
  Locator.localVolumeRepository()
    .then((repo) => repo.createVolume(file.file!!))
    .then(onFinish)
    .catch((error) => {
      message.error(`上传失败:${error}\n文件名: ${file.name}`);
      onError();
    });
};
</script>

<template>
  <section-header title="本地小说" v-if="!hideTitle">
    <n-flex :wrap="false">
      <n-upload
        v-if="!dropzone"
        :show-file-list="false"
        accept=".txt,.epub,.srt"
        multiple
        directory-dnd
        :custom-request="customRequest"
        @before-upload="beforeUpload"
        @finish="onFinish"
      >
        <c-button label="添加文件" :icon="PlusOutlined" />
      </n-upload>

      <n-dropdown
        trigger="click"
        :options="options"
        :keyboard="false"
        @select="handleSelect"
      >
        <n-button circle>
          <n-icon :component="MoreVertOutlined" />
        </n-button>
      </n-dropdown>
    </n-flex>
  </section-header>

  <n-flex vertical>
    <c-action-wrapper title="排序">
      <c-radio-group
        v-model:value="order"
        :options="orderOptions"
        size="small"
      />
    </c-action-wrapper>

    <slot name="extra" />
  </n-flex>

  <n-divider style="margin: 16px 0 8px" />

  <n-upload
    v-if="dropzone"
    :show-file-list="false"
    @finish="onFinish"
    :trigger-style="{ width: '100%' }"
    accept=".txt,.epub,.srt"
    multiple
    directory-dnd
    :custom-request="customRequest"
    @before-upload="beforeUpload"
  >
    <n-upload-dragger>
      <n-text> 点击或者拖动文件到该区域来添加小说 </n-text>
    </n-upload-dragger>
  </n-upload>

  <n-spin v-if="sortedVolumes === undefined" style="margin-top: 20px" />

  <n-empty
    v-else-if="sortedVolumes.length === 0"
    description="没有文件"
    style="margin-top: 20px"
  />

  <n-scrollbar v-else trigger="none" :size="24" style="flex: auto">
    <n-list style="padding-bottom: 48px; padding-right: 12px">
      <n-list-item v-for="volume of sortedVolumes ?? []" :key="volume.id">
        <slot name="volume" v-bind="volume" />
      </n-list-item>
    </n-list>
  </n-scrollbar>

  <c-modal title="清空所有文件" v-model:show="showClearModal">
    <n-p>
      这将清空你的浏览器里面保存的所有EPUB/TXT文件，包括已经翻译的章节和术语表，无法恢复。
      你确定吗？
    </n-p>

    <template #action>
      <c-button label="确定" type="primary" @action="deleteAllVolumes" />
    </template>
  </c-modal>
</template>
