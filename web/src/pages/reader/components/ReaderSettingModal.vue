<script lang="ts" setup>
import { Locator } from '@/data';
import { ReaderSetting } from '@/model/Setting';
import { useIsWideScreen } from '@/pages/util';

const isWideScreen = useIsWideScreen(600);
const setting = Locator.readerSettingRepository().ref;

const setCustomBodyColor = (color: string) =>
  (setting.value.theme.bodyColor = color);
const setCustomFontColor = (color: string) =>
  (setting.value.theme.fontColor = color);
</script>

<template>
  <c-modal title="设置">
    <n-flex vertical size="large" style="width: 100%">
      <c-action-wrapper title="语言">
        <c-radio-group
          v-model:value="setting.mode"
          :options="ReaderSetting.modeOptions"
        />
      </c-action-wrapper>

      <c-action-wrapper title="翻译">
        <n-flex>
          <c-radio-group
            v-model:value="setting.translationsMode"
            :options="ReaderSetting.translationModeOptions"
          />
          <translator-check
            v-model:value="setting.translations"
            show-order
            :two-line="!isWideScreen"
          />
        </n-flex>
      </c-action-wrapper>

      <c-action-wrapper title="字体">
        <c-select-number
          v-model:value="setting.fontSize"
          :options="ReaderSetting.fontSizeOptions"
          :format="(value: number) => `${value}px`"
        />
      </c-action-wrapper>

      <c-action-wrapper title="行距">
        <c-select-number
          v-model:value="setting.lineSpace"
          :options="ReaderSetting.lineSpaceOptions"
          :format="(value: number) => value.toFixed(1)"
        />
      </c-action-wrapper>

      <c-action-wrapper title="主题">
        <n-flex size="large" vertical>
          <c-radio-group
            v-model:value="setting.theme.mode"
            :options="ReaderSetting.themeModeOptions"
          />
          <template v-if="setting.theme.mode === 'custom'">
            <n-flex>
              <n-radio
                v-for="theme of ReaderSetting.themeOptions"
                :checked="theme.bodyColor == setting.theme.bodyColor"
                @update:checked="setting.theme = { mode: 'custom', ...theme }"
              >
                <n-tag
                  :color="{
                    color: theme.bodyColor,
                    textColor: theme.fontColor,
                  }"
                  :style="{
                    width: isWideScreen ? '5.5em' : '2em',
                  }"
                >
                  {{ isWideScreen ? theme.bodyColor : '#' }}
                </n-tag>
              </n-radio>
            </n-flex>
            <n-divider style="margin: 0px" />
            <n-flex>
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
                :default-value="setting.theme.fontColor"
                :on-complete="setCustomFontColor"
                style="width: 8.2em"
              >
                <template #label="color">文字：{{ color }}</template>
              </n-color-picker>
            </n-flex>
          </template>
        </n-flex>
      </c-action-wrapper>

      <c-action-wrapper title="朗读">
        <c-radio-group
          :value="setting.speakLanguages[0]"
          @update-value="(it) => (setting.speakLanguages = [it])"
          :options="ReaderSetting.speakLanguagesOptions"
        />
      </c-action-wrapper>

      <c-action-wrapper title="报错按钮-Sakura" align="center">
        <n-switch
          v-model:value="setting.enableSakuraReportButton"
          size="small"
        />
      </c-action-wrapper>

      <c-action-wrapper title="主透明度" align="center">
        <n-slider
          v-model:value="setting.mixZhOpacity"
          :max="1"
          :min="0"
          :step="0.05"
          :format-tooltip="(value: number) => `${(value*100).toFixed(0)}%`"
        />
      </c-action-wrapper>
      <c-action-wrapper title="辅透明度" align="center">
        <n-slider
          v-model:value="setting.mixJpOpacity"
          :max="1"
          :min="0"
          :step="0.05"
          :format-tooltip="(value: number) => `${(value*100).toFixed(0)}%`"
        />
      </c-action-wrapper>
      <n-text depth="3" style="font-size: 12px">
        # 左/右方向键跳转章节，数字键1～4切换翻译
      </n-text>
    </n-flex>
  </c-modal>
</template>
