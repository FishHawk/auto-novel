<script lang="ts" setup>
import { MoreVertOutlined, DriveFolderUploadOutlined, PlusOutlined } from '@vicons/material';
import { UploadFileInfo } from 'naive-ui';
import { useEventListener } from '@vueuse/core';

import { Locator } from '@/data';
import { LocalVolumeMetadata } from '@/model/LocalVolume';
import { UploadCustomRequestOptions } from 'naive-ui';
import JSZip from 'jszip';
import { downloadFile } from '@/util';

const props = defineProps<{
  hideTitle?: boolean;
  options?: { [key: string]: (volumes: LocalVolumeMetadata[]) => void };
  filter?: (volume: LocalVolumeMetadata) => boolean;
  beforeVolumeAdd?: (file: File) => void;
}>();

const message = useMessage();
const { setting } = Locator.settingRepository();

const volumes = ref<LocalVolumeMetadata[]>();
const fileNameSearch = ref<string>('')

const loadVolumes = async () => {
  const repo = await Locator.localVolumeRepository();
  volumes.value = await repo.listVolume();
  fileNameSearch.value = ''
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
  options.push({ label: '批量下载', key: '批量下载' });
  return options;
});
const handleSelect = (key: string) => {
  switch (key) {
    case '清空文件':
      showClearModal.value = true;
      break;
    case '批量下载':
      downloadVolumes()
      break;

    default:
      props.options?.[key]?.(volumes.value ?? []);
      break;
  }
};

const downloadVolumes = async () => {
  const { mode, translationsMode, translations } = setting.value.downloadFormat;
  const repo = await Locator.localVolumeRepository();
  const zip = new JSZip
  await Promise.all(volumes.value.map(async (volume: LocalVolumeMetadata) => {
    try {
      const { filename, blob } = await repo.getTranslationFile({
        id: volume.id,
        mode,
        translationsMode,
        translations,
      });
      zip.file(filename, blob)
    } catch (error) {
      message.error(`${volume.id} 文件生成错误：${error}`);
    }
  }))

  const blob = await zip.generateAsync({ type: "blob" })
  downloadFile('批量下载.zip', blob)
}

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
  let filteredVolumes =
    props.filter === undefined
      ? volumes.value
      : volumes.value?.filter(props.filter);

  if (fileNameSearch.value) {
    const reg = new RegExp(fileNameSearch.value, 'i')
    filteredVolumes = filteredVolumes.filter((volume: LocalVolumeMetadata) => {
      return reg.test(volume.id)
    })
  }
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

const showDropZone = ref(false);
let dragFlag = { isDragStart: false };
// 将文件从操作系统拖拽到浏览器内，不会触发 dragstart 和 dragend 事件
useEventListener(document, ['dragenter', 'dragstart', 'dragend'], e => {
  if (e.type === 'dragstart') {
    dragFlag.isDragStart = true;
  } else if (e.type === 'dragenter' && !dragFlag.isDragStart) {
    e.preventDefault();
    showDropZone.value = true
  } else if (e.type === 'dragend') {
    dragFlag.isDragStart = false;
  }
});
const handleDragLeave = (e: DragEvent) => {
  e.preventDefault();
  showDropZone.value = false;
}
const handleDrop = (e: DragEvent) => {
  e.preventDefault();
  showDropZone.value = false;
}
</script>

<template>
  <section-header title="本地小说" v-if="!hideTitle">
    <n-flex :wrap="false">
      <n-upload :show-file-list="false" accept=".txt,.epub,.srt" multiple directory-dnd :custom-request="customRequest" @before-upload="beforeUpload" @finish="onFinish">
        <n-tooltip trigger="hover">
          <template #trigger>
            <c-button label="添加文件" :icon="PlusOutlined" />
          </template>
          支持拖拽上传文件
        </n-tooltip>
      </n-upload>

      <n-dropdown trigger="click" :options="options" :keyboard="false" @select="handleSelect">
        <n-button circle>
          <n-icon :component="MoreVertOutlined" />
        </n-button>
      </n-dropdown>
    </n-flex>
  </section-header>

  <n-flex vertical>
    <c-action-wrapper title="排序">
      <c-radio-group v-model:value="order" :options="orderOptions" size="small" />
    </c-action-wrapper>
    <c-action-wrapper title="文件名">
      <n-input v-model:value="fileNameSearch" type="text" placeholder="请输入文件名搜索" />
    </c-action-wrapper>

    <slot name="extra" />
  </n-flex>

  <n-divider style="margin: 16px 0 8px" />

  <n-spin v-if="sortedVolumes === undefined" style="margin-top: 20px" />

  <n-empty v-else-if="sortedVolumes.length === 0" description="没有文件" style="margin-top: 20px" />

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

  <teleport to="body">
    <div class="drop-zone-wrap" v-show="showDropZone">
      <n-upload :show-file-list="false" @finish="onFinish" accept=".txt,.epub,.srt" multiple directory-dnd :custom-request="customRequest" @before-upload="beforeUpload" class="drop-zone" trigger-style="height:100%" @dragleave="handleDragLeave" @drop="handleDrop">
        <n-upload-dragger class="drop-zone-placeholder">
          <n-icon class="drop-icon" :component="DriveFolderUploadOutlined" />
          <div>拖拽文件到这里上传</div>
        </n-upload-dragger>
      </n-upload>
    </div>
  </teleport>
</template>

<style scoped>
.drop-zone-wrap {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.7);
  z-index: 2000;
  box-sizing: border-box;
}

.drop-zone {
  width: 100%;
  height: 100%;
  cursor: pointer;
  box-sizing: border-box;
}

.drop-zone-placeholder {
  pointer-events: none;
  position: fixed;
  left: 42px;
  top: 42px;
  right: 42px;
  bottom: 42px;
  width: auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #fff;
  background-color: transparent;
  font-size: 24px;
  border-radius: 12px;
  border-width: 2px !important;
}

.drop-icon {
  font-size: 48px;
  margin-bottom: 16px;
}
</style>