<script lang="ts" setup>
import { createReusableTemplate } from '@vueuse/core';
import { ref } from 'vue';

import {
  fontSizeOptions,
  lineSpaceOptions,
  modeOptions,
  themeOptions,
  translationModeOptions,
  useReaderSettingStore,
} from '@/data/stores/reader_setting';
import { useIsWideScreen } from '@/data/util';

const [DefineOption, ReuseOption] = createReusableTemplate<{
  label: string;
  align: 'baseline' | 'center';
}>();

const isWideScreen = useIsWideScreen(600);
const setting = useReaderSettingStore();

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

    <n-flex vertical size="large" style="width: 100%">
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
        </n-flex>
      </ReuseOption>
      <ReuseOption label="字体" align="baseline">
        <c-select-number
          v-model:value="setting.fontSize"
          :options="fontSizeOptions"
          :format="(value: number) => `${value}px`"
        />
      </ReuseOption>

      <ReuseOption label="行距" align="baseline">
        <c-select-number
          v-model:value="setting.lineSpace"
          :options="lineSpaceOptions"
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
              style="width: 8.2em"
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
              style="width: 8.2em"
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
    </n-flex>
  </c-modal>
</template>
