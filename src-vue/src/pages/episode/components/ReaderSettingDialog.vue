<script lang="ts" setup>
import { computed } from 'vue';
import { useWindowSize } from '@vueuse/core';

import { useReaderSettingStore } from '@/data/stores/readerSetting';

const { width } = useWindowSize();
const isDesktop = computed(() => width.value > 600);

const modeOptions = [
  { value: 'jp', label: '日文' },
  { value: 'zh', label: '中文' },
  { value: 'mix', label: '中文/日文' },
  { value: 'mix-reverse', label: '日文/中文' },
];
const translationOptions = [
  { value: 'youdao', label: '有道优先' },
  { value: 'baidu', label: '百度优先' },
  { value: 'youdao/baidu', label: '有道/百度' },
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
  <n-modal :show="show" @update:show="$emit('update:show', $event)">
    <n-card
      style="width: min(600px, calc(100% - 16px))"
      title="设置"
      :bordered="false"
      size="huge"
      role="dialog"
      aria-modal="true"
    >
      <n-space vertical size="large" style="width: 100%">
        <ReaderSettingDialogSelect
          :desktop="isDesktop"
          label="语言"
          v-model:value="setting.mode"
          :options="modeOptions"
        />
        <ReaderSettingDialogSelect
          :desktop="isDesktop"
          label="翻译"
          v-model:value="setting.translation"
          :options="translationOptions"
        />
        <ReaderSettingDialogSelect
          :desktop="isDesktop"
          label="字体"
          v-model:value="setting.fontSize"
          :options="fontSizeOptions"
        />
        <ReaderSettingDialogSelect label="主题">
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
        </ReaderSettingDialogSelect>

        <ReaderSettingDialogSelect label="日文透明度">
          <n-slider
            v-model:value="setting.mixJpOpacity"
            :max="1"
            :min="0"
            :step="0.05"
            :format-tooltip="(value: number) => `${(value*100).toFixed(0)}%`"
          />
        </ReaderSettingDialogSelect>
      </n-space>
    </n-card>
  </n-modal>
</template>

<style scoped>
.label {
  margin-right: 24px;
  white-space: nowrap;
}
</style>
