<script lang="ts" setup>
import {
  DeleteOutlineOutlined,
  RemoveRedEyeOutlined,
  DownloadOutlined,
  SettingsOutlined,
} from '@vicons/material';
import { Epub, Txt } from '@/util/file';
import { downloadFile } from '@/util';
import { EpubSetting } from './EpubSettingModal.vue';

defineProps<{
  file: Epub | Txt;
}>();

const emit = defineEmits<{
  delete: [];
}>();

const showPreviewModal = ref(false);
const showEpubSettingModal = ref(false);
const setting = ref<EpubSetting>({
  img: {
    format: '',
    compression_rate: 1,
    ratio: 1,
    blacklist: [],
  },
});

const compressEpubImagesAndDownload = async (epub_file: Epub) => {
  const file = epub_file.copy(); //创建深层复制，防止对文件造成不可逆的修改
  let compress_setting = setting.value.img;
  if (
    compress_setting.format === '' &&
    compress_setting.compression_rate === 1 &&
    compress_setting.ratio === 1
  ) {
    return;
  }
  file.getImages().forEach(async (e) => {
    if (compress_setting.blacklist.includes(e.path)) {
      return;
    }
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d')!;
    const img = await createImageBitmap(e.blob);
    // 计算缩放后尺寸
    const scale = compress_setting.ratio > 1 ? 1 : compress_setting.ratio;
    const width = img.width * scale;
    const height = img.height * scale;
    // 绘制到画布
    canvas.width = width;
    canvas.height = height;
    ctx.drawImage(img, 0, 0, width, height);
    // 转换格式
    const quality = Math.min(1, compress_setting.compression_rate);
    const mimeType = compress_setting.format
      ? compress_setting.format
      : undefined;
    const newPath = mimeType
      ? e.path.replace(/(jpg|jpeg|png|webp)$/i, mimeType.split('/')[1])
      : e.path;
    e.blob = await new Promise((resolve, reject) => {
      canvas.toBlob(
        (newBlob) => {
          if (newBlob) {
            resolve(newBlob);
          } else {
            reject(new Error(`压缩图片${e.path}失败`));
          }
        },
        mimeType,
        quality,
      );
    });
    // 更新图片的路径与相关引用
    file.updateLinks(e.path, newPath, mimeType);
    e.path = newPath;
  });

  downloadFile(file.name, await file.toBlob());
};
</script>

<template>
  <n-thing v-if="file instanceof Epub" content-indented>
    <template #header>
      {{ file.name }}
    </template>
    <template #header-extra>
      <n-flex :wrap="false" align="center">
        <c-icon-button
          tooltip="设置"
          :icon="SettingsOutlined"
          @action="showEpubSettingModal = !showEpubSettingModal"
        />
        <!-- 压缩图片并下载Epub -->
        <c-button
          label="压缩图片"
          :icon="DownloadOutlined"
          size="tiny"
          secondary
          @action="compressEpubImagesAndDownload(file)"
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

  <n-flex v-else :size="4" aign="center" style="font-size: 12px" :wrap="false">
    <c-icon-button
      tooltip="预览"
      :icon="RemoveRedEyeOutlined"
      text
      size="small"
      type="primary"
      @action="showPreviewModal = true"
    />
    <c-icon-button
      tooltip="移除"
      :icon="DeleteOutlineOutlined"
      text
      size="small"
      type="error"
      @action="emit('delete')"
    />
    <n-text>{{ file.name }}</n-text>
  </n-flex>

  <c-modal title="预览（前100行）" v-model:show="showPreviewModal">
    <n-text>暂未实现</n-text>
  </c-modal>

  <epub-setting-modal
    v-if="file instanceof Epub"
    v-model:show="showEpubSettingModal"
    :images="file.getImages()"
    :setting="setting"
  />
</template>
