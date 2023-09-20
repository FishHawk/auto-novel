<script lang="ts" setup>
import { createReusableTemplate } from '@vueuse/core';

import { useSettingStore } from '@/data/stores/setting';
import { useIsDesktop } from '@/data/util';

const [DefineOption, ReuseOption] = createReusableTemplate<{
  label: string;
}>();

const isDesktop = useIsDesktop(600);

const modeOptions = [
  { value: 'zh', label: '中文' },
  { value: 'mix', label: '中文/日文' },
  { value: 'mix-reverse', label: '日文/中文' },
];
const translationModeOptions = [
  { label: '优先', value: 'priority' },
  { label: '并列', value: 'parallel' },
];
const translationOptions = [
  { label: 'GPT3', value: 'gpt' },
  { label: '有道', value: 'youdao' },
  { label: '百度', value: 'baidu' },
];

const setting = useSettingStore();

defineProps<{
  show: boolean;
}>();

defineEmits<{
  (e: 'update:show', show: boolean): void;
}>();
</script>

<template>
  <DefineOption v-slot="{ $slots, label }">
    <tr>
      <td nowrap="nowrap" style="padding-right: 12px">{{ label }}</td>
      <td style="width: 100%"><component :is="$slots.default!" /></td>
    </tr>
  </DefineOption>

  <n-modal :show="show" @update:show="$emit('update:show', $event)">
    <n-card
      style="width: min(600px, calc(100% - 16px))"
      title="下载设置"
      :bordered="false"
      size="huge"
      role="dialog"
      aria-modal="true"
    >
      <n-space vertical size="large" style="width: 100%">
        <ReuseOption label="与阅读设置一致">
          <n-switch
            :rubber-band="false"
            size="small"
            v-model:value="setting.isDownloadFormatSameAsReaderFormat"
          />
        </ReuseOption>

        <ReuseOption label="语言">
          <ReaderSettingDialogSelect
            :desktop="isDesktop"
            v-model:value="setting.downloadFormat.mode"
            :options="modeOptions"
            :disabled="setting.isDownloadFormatSameAsReaderFormat"
          />
        </ReuseOption>
        <ReuseOption label="翻译">
          <ReaderSettingDialogSelect
            :desktop="isDesktop"
            v-model:value="setting.downloadFormat.translationsMode"
            :options="translationModeOptions"
            :disabled="setting.isDownloadFormatSameAsReaderFormat"
          />
          <n-transfer
            v-model:value="setting.downloadFormat.translations"
            :options="translationOptions"
            :disabled="setting.isDownloadFormatSameAsReaderFormat"
            style="height: 160px; margin-top: 8px"
          />
        </ReuseOption>
        <n-text depth="3" style="font-size: 12px">
          # 部分Epub阅读器不支持自定义字体颜色
        </n-text>
      </n-space>
    </n-card>
  </n-modal>
</template>

<style>
.n-transfer-list-header__extra {
  display: none;
}
</style>
