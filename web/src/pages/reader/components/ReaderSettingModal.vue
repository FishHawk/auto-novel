<script lang="ts" setup>
import { createReusableTemplate } from '@vueuse/core';
import { ref } from 'vue';

import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { TranslatorId } from '@/data/translator';
import { useIsWideScreen } from '@/data/util';

const [DefineOption, ReuseOption] = createReusableTemplate<{
  label: string;
  align: 'baseline' | 'center';
}>();

const isWideScreen = useIsWideScreen(600);
const setting = useReaderSettingStore();

const modeOptions = [
  { value: 'jp', label: '日文' },
  { value: 'zh', label: '中文' },
  { value: 'mix', label: '中日' },
  { value: 'mix-reverse', label: '日中' },
];

const translationModeOptions = [
  { label: '优先', value: 'priority' },
  { label: '并列', value: 'parallel' },
];
const translationOptions: { label: string; value: TranslatorId }[] = [
  { label: 'Sakura', value: 'sakura' },
  { label: 'GPT', value: 'gpt' },
  { label: '有道', value: 'youdao' },
  { label: '百度', value: 'baidu' },
];
const toggleTranslator = (id: TranslatorId) => {
  if (setting.translations.includes(id)) {
    setting.translations = setting.translations.filter((it) => it !== id);
  } else {
    setting.translations.push(id);
  }
};
const calculateTranslatorOrderLabel = (id: TranslatorId) => {
  const index = setting.translations.indexOf(id);
  if (index < 0) {
    return '[x]';
  } else {
    return `[${index + 1}]`;
  }
};

// 兼容旧格式
if (typeof setting.fontSize === 'string') {
  setting.fontSize = Number((setting.fontSize as any).replace(/[^0-9]/g, ''));
}

const themeOptions = [
  { isDark: false, bodyColor: '#FFFFFF' },
  { isDark: false, bodyColor: '#FFF2E2' },
  { isDark: false, bodyColor: '#E3EDCD' },
  { isDark: false, bodyColor: '#E9EBFE' },
  { isDark: false, bodyColor: '#EAEAEF' },

  { isDark: true, bodyColor: '#000000' },
  { isDark: true, bodyColor: '#272727' },
];

const showCustomThemeControls = ref(false);
const setCustomBodyColor = (color: string) => {
  setting.theme.bodyColor = color;
  const r = parseInt(color.substring(1, 3), 16);
  const g = parseInt(color.substring(3, 5), 16);
  const b = parseInt(color.substring(5, 7), 16);
  const brightness = (r * 299 + g * 587 + b * 114) / 1000;
  setting.theme.isDark = brightness < 120;
};
const setCustomFontColor = (color: string) => {
  setting.theme.fontColor = color;
};
</script>

<template>
  <c-modal title="设置">
    <DefineOption v-slot="{ $slots, label, align }">
      <n-flex :wrap="false" :align="align">
        <n-text depth="3" style="white-space: nowrap; font-size: 12px">
          {{ label }}
        </n-text>
        <component :is="$slots.default!" />
      </n-flex>
    </DefineOption>

    <n-space vertical size="large" style="width: 100%">
      <ReuseOption label="语言" align="baseline">
        <n-radio-group v-model:value="setting.mode">
          <n-radio-button
            v-for="option in modeOptions"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          />
        </n-radio-group>
      </ReuseOption>
      <ReuseOption label="翻译" align="baseline">
        <n-flex>
          <n-radio-group v-model:value="setting.translationsMode">
            <n-radio-button
              v-for="option in translationModeOptions"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </n-radio-group>
          <translator-check
            v-model:value="setting.translations"
            show-order
            :two-line="!isWideScreen"
          />
          <!-- <n-button-group>
            <n-button
              v-for="option in translationOptions"
              :focusable="false"
              ghost
              :type="
                setting.translations.includes(option.value)
                  ? 'primary'
                  : 'default'
              "
              :value="option.value"
              @click="toggleTranslator(option.value)"
              :style="isWideScreen ? {} : { height: '48px' }"
            >
              {{ option.label }}
              <br v-if="!isWideScreen" />
              {{ calculateTranslatorOrderLabel(option.value) }}
            </n-button>
          </n-button-group> -->
        </n-flex>
      </ReuseOption>
      <ReuseOption label="字体" align="baseline">
        <c-select-number
          v-model:value="setting.fontSize"
          :options="[14, 16, 18, 20, 24, 30, 40]"
          :format="(value: number) => `${value}px`"
        />
      </ReuseOption>

      <ReuseOption label="行距" align="baseline">
        <c-select-number
          v-model:value="setting.lineSpace"
          :options="[0.0, 0.2, 0.4, 0.6, 0.8, 1.0]"
          :format="(value: number) => value.toFixed(1)"
        />
      </ReuseOption>
      <ReuseOption label="主题" align="baseline">
        <n-flex size="large" vertical>
          <n-flex>
            <n-radio
              v-for="theme of themeOptions"
              :checked="theme.bodyColor == setting.theme.bodyColor"
              @update:checked="setting.theme = { ...theme }"
            >
              <n-tag
                :round="!isWideScreen"
                :color="{
                  color: theme.bodyColor,
                  textColor: theme.isDark ? 'white' : 'black',
                }"
                :style="{
                  width: isWideScreen ? '5.5em' : '2em',
                }"
              >
                {{ isWideScreen ? theme.bodyColor : '#' }}
              </n-tag>
            </n-radio>
            <n-button
              type="primary"
              text
              @click="showCustomThemeControls = !showCustomThemeControls"
            >
              自定义
            </n-button>
          </n-flex>
          <n-flex v-if="showCustomThemeControls" align="center">
            <n-color-picker
              :modes="['hex']"
              :show-alpha="false"
              :default-value="setting.theme.bodyColor"
              :on-complete="setCustomBodyColor"
              style="width: 8em"
            >
              <template #label="color">背景：{{ color }}</template>
            </n-color-picker>
            <n-color-picker
              :modes="['hex']"
              :show-alpha="false"
              :default-value="
                setting.theme.fontColor ??
                (setting.theme.isDark ? '#FFFFFF' : '#000000')
              "
              :on-complete="setCustomFontColor"
              style="width: 8em"
            >
              <template #label="color">文字：{{ color }}</template>
            </n-color-picker>
          </n-flex>
        </n-flex>
      </ReuseOption>
      <ReuseOption label="Sakura报错按钮" align="center">
        <n-switch
          v-model:value="setting.enableSakuraReportButton"
          size="small"
        />
      </ReuseOption>

      <ReuseOption label="主透明度" align="center">
        <n-slider
          v-model:value="setting.mixZhOpacity"
          :max="1"
          :min="0"
          :step="0.05"
          :format-tooltip="(value: number) => `${(value*100).toFixed(0)}%`"
        />
      </ReuseOption>
      <ReuseOption label="辅透明度" align="center">
        <n-slider
          v-model:value="setting.mixJpOpacity"
          :max="1"
          :min="0"
          :step="0.05"
          :format-tooltip="(value: number) => `${(value*100).toFixed(0)}%`"
        />
      </ReuseOption>
      <n-text depth="3" style="font-size: 12px">
        # 左/右方向键跳转章节，数字键1～4切换翻译
      </n-text>
    </n-space>
  </c-modal>
</template>

<style>
.n-transfer-list-header__extra {
  display: none;
}
</style>
