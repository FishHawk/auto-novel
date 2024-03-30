<script lang="ts" setup>
import { DeleteOutlineOutlined } from '@vicons/material';

import { GenericNovelId } from '@/model/Common';

import LocalVolumeList from './LocalVolumeList.vue';

const emit = defineEmits<{ volumeLoaded: [string] }>();

const localVolumeListRef = ref<InstanceType<typeof LocalVolumeList>>();
</script>

<template>
  <local-volume-list ref="localVolumeListRef">
    <template #volume="volume">
      <n-flex :size="4" vertical>
        <n-text>{{ volume.id }}</n-text>

        <n-text depth="3">
          <n-time :time="volume.createAt" type="relative" /> / 总计
          {{ volume.toc.length }}
        </n-text>

        <n-flex :size="8">
          <c-button
            label="载入"
            size="tiny"
            secondary
            @action="emit('volumeLoaded', volume.id)"
          />

          <glossary-button
            :gnid="GenericNovelId.local(volume.id)"
            :value="volume.glossary"
            size="tiny"
            secondary
          />

          <div style="flex: 1" />

          <n-popconfirm
            :show-icon="false"
            @positive-click="localVolumeListRef?.deleteVolume(volume.id)"
            :negative-text="null"
            style="max-width: 300px"
          >
            <template #trigger>
              <c-icon-button :icon="DeleteOutlineOutlined" type="error" />
            </template>
            真的要删除吗？
            <br />
            {{ volume.id }}
          </n-popconfirm>
        </n-flex>
      </n-flex>
    </template>
  </local-volume-list>
</template>
