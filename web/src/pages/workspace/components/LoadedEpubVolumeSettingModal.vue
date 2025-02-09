<script lang="ts" setup>
import { EpubSetting } from '@/util/file/epub';

const props = defineProps<{
  show: boolean;
  images: {
    path: string;
    url: string;
  }[];
  setting: EpubSetting;
}>();

const showImgs = ref(false);

const imgFormatOptions = [
  { label: '原始', value: '' },
  { label: 'PNG', value: 'image/png' },
  { label: 'JPG', value: 'image/jpeg' },
  { label: 'WEBP', value: 'image/webp' },
];
</script>

<template>
  <c-modal
    :show="show"
    @update:show="$emit('update:show', $event)"
    title="Epub设置"
    content-style="padding: 0;"
  >
    <n-tabs
      type="line"
      size="large"
      :tabs-padding="20"
      pane-style="padding: 0px;"
      animated
      style="width: 100%"
    >
      <n-tab-pane name="img" tab="图像">
        <n-flex vertical size="large" style="width: 100%; padding: 20px">
          <c-action-wrapper title="格式">
            <c-radio-group
              v-model:value="setting.img.format"
              :options="imgFormatOptions"
            />
          </c-action-wrapper>

          <c-action-wrapper title="压缩质量" align="center">
            <n-slider
              v-model:value="setting.img.compression_rate"
              :max="1"
              :min="0"
              :step="0.05"
              :format-tooltip="
                (value: number) => `${(value * 100).toFixed(0)}%`
              "
              style="flex: auto"
            />
            <n-text style="width: 6em">
              {{ (setting.img.compression_rate * 100).toFixed(0) }}%
            </n-text>
          </c-action-wrapper>

          <c-action-wrapper title="图片大小" align="center">
            <n-slider
              v-model:value="setting.img.ratio"
              :max="1"
              :min="0"
              :step="0.05"
              :format-tooltip="
                (value: number) => `${(value * 100).toFixed(0)}%`
              "
              style="flex: auto"
            />
            <n-text style="width: 6em">
              {{ (setting.img.ratio * 100).toFixed(0) }}%
            </n-text>
          </c-action-wrapper>

          <c-action-wrapper title="黑名单（不处理的图片）" align="center">
            <c-button
              :label="`选中图片 (${setting.img.blacklist.length})`"
              size="tiny"
              secondary
              @action="showImgs = !showImgs"
            />
          </c-action-wrapper>
        </n-flex>
      </n-tab-pane>
    </n-tabs>
  </c-modal>
  <c-image-selector-modal
    :images="images"
    v-model:value="setting.img.blacklist"
    v-model:show="showImgs"
  >
  </c-image-selector-modal>
</template>
