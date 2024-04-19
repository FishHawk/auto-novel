<script lang="ts" setup>
import { Setting } from '@/model/Setting';
import { Locator } from '@/data';

import LocalVolumeList from './LocalVolumeList.vue';

const { setting } = Locator.settingRepository();

const localVolumeListRef = ref<InstanceType<typeof LocalVolumeList>>();
</script>

<template>
  <local-volume-list ref="localVolumeListRef">
    <template #extra>
      <c-action-wrapper title="语言">
        <c-radio-group
          v-model:value="setting.downloadFormat.mode"
          :options="Setting.downloadModeOptions"
          size="small"
        />
      </c-action-wrapper>

      <c-action-wrapper title="翻译">
        <n-flex>
          <c-radio-group
            v-model:value="setting.downloadFormat.translationsMode"
            :options="Setting.downloadTranslationModeOptions"
            size="small"
          />
          <translator-check
            v-model:value="setting.downloadFormat.translations"
            show-order
            size="small"
          />
        </n-flex>
      </c-action-wrapper>
    </template>

    <template #volume="volume">
      <local-volume-list-composite-item :volume="volume">
        <n-popconfirm
          :show-icon="false"
          @positive-click="localVolumeListRef?.deleteVolume(volume.id)"
          :negative-text="null"
          style="max-width: 300px"
        >
          <template #trigger>
            <c-button label="删除" type="error" size="tiny" secondary />
          </template>
          真的要删除吗？
          <br />
          {{ volume.id }}
        </n-popconfirm>
      </local-volume-list-composite-item>
    </template>
  </local-volume-list>
</template>
