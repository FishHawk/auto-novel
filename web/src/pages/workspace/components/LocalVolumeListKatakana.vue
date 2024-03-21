<script lang="ts" setup>
import { localGnid } from '@/model/Common';

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
            label="添加"
            size="tiny"
            secondary
            @action="emit('volumeLoaded', volume.id)"
          />

          <glossary-button
            :gnid="localGnid(volume.id)"
            :value="volume.glossary"
            size="tiny"
            secondary
          />

          <n-popconfirm
            :show-icon="false"
            @positive-click="localVolumeListRef?.deleteVolume(volume.id)"
            :negative-text="null"
          >
            <template #trigger>
              <c-button label="删除" type="error" size="tiny" secondary />
            </template>
            确定删除{{ volume.id }}吗？
          </n-popconfirm>
        </n-flex>
      </n-flex>
    </template>
  </local-volume-list>
</template>
