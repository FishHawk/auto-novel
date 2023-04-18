<script lang="ts" setup>
import { useReaderSettingStore } from '../../../data/stores/readerSetting';

const modeOptions = [
  { value: 'jp', label: '日文' },
  { value: 'zh', label: '中文' },
  { value: 'mix', label: '中文/日文' },
  { value: 'mix-reverse', label: '日文/中文' },
];
const translationOptions = [
  { value: 'youdao', label: '有道' },
  { value: 'baidu', label: '百度' },
  { value: 'youdao/baidu', label: '有道/百度' },
];
const fontSizeOptions = ['14px', '16px', '18px', '20px'];
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
      <n-space vertical size="large">
        <n-space :wrap="false">
          <span class="label">语言</span>
          <n-radio-group v-model:value="setting.mode" name="mode">
            <n-space>
              <n-radio
                v-for="option in modeOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </n-radio>
            </n-space>
          </n-radio-group>
        </n-space>

        <n-space :wrap="false">
          <span class="label">翻译</span>
          <n-radio-group v-model:value="setting.translation" name="translator">
            <n-space>
              <n-radio
                v-for="option in translationOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </n-radio>
            </n-space>
          </n-radio-group>
        </n-space>

        <n-space :wrap="false">
          <span class="label">字体</span>
          <n-radio-group v-model:value="setting.fontSize" name="fontSize">
            <n-space>
              <n-radio
                v-for="option in fontSizeOptions"
                :key="option"
                :value="option"
              >
                {{ option }}
              </n-radio>
            </n-space>
          </n-radio-group>
        </n-space>

        <n-space :wrap="false">
          <span class="label">主题</span>
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
                style="width: 8em"
              >
                {{ theme.bodyColor }}
              </n-tag>
            </n-radio>
          </n-space>
        </n-space>

        <span class="label">日文透明度</span>
        <n-slider
          v-model:value="setting.mixJpOpacity"
          :max="1"
          :min="0"
          :step="0.05"
          :format-tooltip="(value: number) => `${(value*100).toFixed(0)}%`"
        />
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
