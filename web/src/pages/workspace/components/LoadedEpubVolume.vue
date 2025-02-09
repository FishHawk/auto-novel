<script lang="ts" setup>
import { downloadFile } from '@/util';
import { getFullContent, Epub } from '@/util/file';
import {
  DownloadOutlined,
  DeleteOutlineOutlined,
  SettingsOutlined,
  AutoFixHighOutlined,
} from '@vicons/material';
import { EpubSetting } from './EpubSettingModal.vue';

const props = defineProps<{
  volume: {
    filename: string;
    file: File;
    content: string;
    images: { path: string; url: string }[];
  };
}>();

const emit = defineEmits<{
  delete: [];
}>();

const showSettingModal = ref(false);
const setting = ref<EpubSetting>({
  img: {
    format: '',
    compression_rate: 1,
    ratio: 1,
    blacklist: [],
  },
});

const downloadAsTxt = async () => {
  const volume = props.volume;
  downloadFile(
    volume.filename.replace(/\.epub$/, '.txt'),
    new Blob([await getFullContent(volume.file)], { type: 'text/plain' }),
  );
};

const processEpub = async () => {
  // const volume = props.volume;
  // downloadFile(
  //   volume.filename,
  //   await Epub.processEpub(volume.file, setting.value)
  // );
};
</script>

<template>
  <n-thing content-indented>
    <template #header>
      {{ volume.filename }}
    </template>
    <template #header-extra>
      <n-flex :wrap="false" align="center">
        <c-icon-button
          tooltip="设置"
          :icon="SettingsOutlined"
          @action="showSettingModal = !showSettingModal"
        />
        <!-- 处理并下载Epub -->
        <c-button
          label="处理"
          :icon="AutoFixHighOutlined"
          size="tiny"
          secondary
          @action="processEpub"
        />
        <!-- 下载Txt -->
        <c-button
          label="TXT"
          :icon="DownloadOutlined"
          size="tiny"
          secondary
          @action="downloadAsTxt"
        />
        <!-- 删除 -->
        <c-icon-button
          tooltip="删除"
          :icon="DeleteOutlineOutlined"
          type="error"
          @action="emit('delete')"
        />
      </n-flex>
    </template>
  </n-thing>

  <!-- <loaded-epub-volume-setting-modal
    v-model:show="showSettingModal"
    :setting="setting"
    :images="volume.images"
  /> -->
</template>
