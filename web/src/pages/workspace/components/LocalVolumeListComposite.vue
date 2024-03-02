<script lang="ts" setup>
import { ref } from 'vue';

import {
  downloadModeOptions,
  downloadTranslationModeOptions,
  useSettingStore,
} from '@/data/stores/setting';

import LocalVolumeList from './LocalVolumeList.vue';

const setting = useSettingStore();

const localVolumeListRef = ref<InstanceType<typeof LocalVolumeList>>();
</script>

<template>
  <local-volume-list ref="localVolumeListRef">
    <template #extra>
      <n-flex align="baseline" :wrap="false">
        <n-text style="white-space: nowrap">语言</n-text>
        <n-radio-group v-model:value="setting.downloadFormat.mode" size="small">
          <n-radio-button
            v-for="option in downloadModeOptions"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          />
        </n-radio-group>
      </n-flex>

      <n-flex align="baseline" :wrap="false">
        <n-text style="white-space: nowrap">翻译</n-text>
        <n-flex>
          <n-radio-group
            v-model:value="setting.downloadFormat.translationsMode"
            size="small"
          >
            <n-radio-button
              v-for="option in downloadTranslationModeOptions"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </n-radio-group>
          <translator-check
            v-model:value="setting.downloadFormat.translations"
            show-order
            size="small"
          />
        </n-flex>
      </n-flex>
    </template>

    <template #volume="volume">
      <local-volume-list-composite-item :volume="volume">
        <n-popconfirm
          :show-icon="false"
          @positive-click="localVolumeListRef?.deleteVolume(volume.id)"
          :negative-text="null"
        >
          <template #trigger>
            <c-button label="删除" type="error" size="tiny" secondary />
          </template>
          真的要删除{{ volume.id }}吗？
        </n-popconfirm>
      </local-volume-list-composite-item>
    </template>
  </local-volume-list>
</template>
