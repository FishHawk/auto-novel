<script lang="ts" setup>
import { Epub } from '@/util/file';

const compressionRate = ref(0.8);
const scaleRatio = ref(1.0);

const imageFormat = ref('image/webp');
const imageFormatOptions = [
  { label: '不改变图片格式', value: '' },
  { label: 'PNG', value: 'image/png' },
  { label: 'JPEG', value: 'image/jpeg' },
  { label: 'WEBP', value: 'image/webp' },
];

const compressEpubImages = async () => {
  //   const file = epub_file.copy(); //创建深层复制，防止对文件造成不可逆的修改
  //   if (
  //     compress_setting.format === '' &&
  //     compress_setting.compression_rate === 1 &&
  //     compress_setting.ratio === 1
  //   ) {
  //     return;
  //   }
  //   file.getImages().forEach(async (e) => {
  //     if (compress_setting.blacklist.includes(e.path)) {
  //       return;
  //     }
  //     const canvas = document.createElement('canvas');
  //     const ctx = canvas.getContext('2d')!;
  //     const img = await createImageBitmap(e.blob);
  //     // 计算缩放后尺寸
  //     const scale = compress_setting.ratio > 1 ? 1 : compress_setting.ratio;
  //     const width = img.width * scale;
  //     const height = img.height * scale;
  //     // 绘制到画布
  //     canvas.width = width;
  //     canvas.height = height;
  //     ctx.drawImage(img, 0, 0, width, height);
  //     // 转换格式
  //     const quality = Math.min(1, compress_setting.compression_rate);
  //     const mimeType = compress_setting.format
  //       ? compress_setting.format
  //       : undefined;
  //     const newPath = mimeType
  //       ? e.path.replace(/(jpg|jpeg|png|webp)$/i, mimeType.split('/')[1])
  //       : e.path;
  //     e.blob = await new Promise((resolve, reject) => {
  //       canvas.toBlob(
  //         (newBlob) => {
  //           if (newBlob) {
  //             resolve(newBlob);
  //           } else {
  //             reject(new Error(`压缩图片${e.path}失败`));
  //           }
  //         },
  //         mimeType,
  //         quality,
  //       );
  //     });
  //     // 更新图片的路径与相关引用
  //     file.updateLinks(e.path, newPath, mimeType);
  //     e.path = newPath;
  //   });
};
</script>

<template>
  <n-flex vertical>
    <b>EPUB：压缩图片</b>

    <n-flex vertical>
      <c-action-wrapper title="格式">
        <c-radio-group
          v-model:value="imageFormat"
          :options="imageFormatOptions"
          size="small"
        />
      </c-action-wrapper>

      <c-action-wrapper title="压缩率" align="center">
        <n-slider
          v-model:value="compressionRate"
          :max="1"
          :min="0.1"
          :step="0.05"
          :format-tooltip="(value: number) => `${(value * 100).toFixed(0)}%`"
          style="max-width: 400px"
        />
        <n-text style="width: 6em">
          {{ (compressionRate * 100).toFixed(0) }}%
        </n-text>
      </c-action-wrapper>

      <c-action-wrapper title="尺寸" align="center">
        <n-slider
          v-model:value="scaleRatio"
          :max="1"
          :min="0.1"
          :step="0.05"
          :format-tooltip="(value: number) => `${(value * 100).toFixed(0)}%`"
          style="max-width: 400px"
        />
        <n-text style="width: 6em">
          {{ (scaleRatio * 100).toFixed(0) }}%
        </n-text>
      </c-action-wrapper>

      <!--
      <c-action-wrapper title="黑名单（不处理的图片）" align="center">
        <c-button
          :label="`选中图片 (${setting.img.blacklist.length})`"
          size="tiny"
          secondary
          @action="showImgs = !showImgs"
        />
      </c-action-wrapper> -->

      <n-flex>
        <c-button label="确定" size="small" @action="compressEpubImages" />
      </n-flex>
    </n-flex>
  </n-flex>
</template>
