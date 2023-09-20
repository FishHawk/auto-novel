<script lang="ts" setup>
import { createReusableTemplate } from '@vueuse/core';

import { useReaderSettingStore } from '@/data/stores/readerSetting';
import { useIsDesktop } from '@/data/util';

const [DefineOption, ReuseOption] = createReusableTemplate<{
  label: string;
}>();

const isDesktop = useIsDesktop(600);

const modeOptions = [
  { value: 'jp', label: '日文' },
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
const fontSizeOptions = [
  { value: '14px', label: '14px' },
  { value: '16px', label: '16px' },
  { value: '18px', label: '18px' },
  { value: '20px', label: '20px' },
];
const themeOptions = [
  { isDark: false, bodyColor: '#FFFFFF' },
  { isDark: false, bodyColor: '#FFF2E2' },
  { isDark: false, bodyColor: '#E3EDCD' },
  { isDark: false, bodyColor: '#E9EBFE' },
  { isDark: false, bodyColor: '#EAEAEF' },

  { isDark: true, bodyColor: '#000000' },
  { isDark: true, bodyColor: '#272727' },
];

const setting = useReaderSettingStore();

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
      title="阅读设置"
      :bordered="false"
      size="huge"
      role="dialog"
      aria-modal="true"
    >
      <n-space vertical size="large" style="width: 100%">
        <ReuseOption label="语言">
          <ReaderSettingDialogSelect
            :desktop="isDesktop"
            v-model:value="setting.mode"
            :options="modeOptions"
          />
        </ReuseOption>
        <ReuseOption label="翻译">
          <ReaderSettingDialogSelect
            :desktop="isDesktop"
            v-model:value="setting.translationsMode"
            :options="translationModeOptions"
          />
          <n-transfer
            v-model:value="setting.translations"
            :options="translationOptions"
            style="height: 160px; margin-top: 8px"
          />
        </ReuseOption>
        <ReuseOption label="字体">
          <ReaderSettingDialogSelect
            :desktop="isDesktop"
            v-model:value="setting.fontSize"
            :options="fontSizeOptions"
          />
        </ReuseOption>
        <ReuseOption label="主题">
          <n-space>
            <n-radio
              v-for="theme of themeOptions"
              :checked="theme.bodyColor == setting.theme.bodyColor"
              @update:checked="setting.theme = theme"
            >
              <n-tag
                :color="{
                  color: theme.bodyColor,
                  textColor: theme.isDark ? 'white' : 'black',
                }"
                :style="{
                  width: isDesktop ? '7em' : '1.7em',
                }"
              >
                {{ isDesktop ? theme.bodyColor : 'A' }}
              </n-tag>
            </n-radio>
          </n-space>
        </ReuseOption>
        <ReuseOption label="主文本透明度">
          <n-slider
            v-model:value="setting.mixZhOpacity"
            :max="1"
            :min="0"
            :step="0.05"
            :format-tooltip="(value: number) => `${(value*100).toFixed(0)}%`"
          />
        </ReuseOption>
        <ReuseOption label="辅文本透明度">
          <n-slider
            v-model:value="setting.mixJpOpacity"
            :max="1"
            :min="0"
            :step="0.05"
            :format-tooltip="(value: number) => `${(value*100).toFixed(0)}%`"
          />
        </ReuseOption>
        <n-text depth="3" style="font-size: 12px">
          # 左/右方向键可以跳转上/下一章
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
