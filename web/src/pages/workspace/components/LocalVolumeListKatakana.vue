<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref, toRaw } from 'vue';

import { PersonalVolumesManager } from '@/data/translator';
import { LocalVolumeMetadata } from '@/data/translator/db/personal';

import LocalVolumeList from './LocalVolumeList.vue';

const emit = defineEmits<{ volumeLoaded: [string] }>();

const message = useMessage();

const localVolumeListRef = ref<InstanceType<typeof LocalVolumeList>>();

const showGlossaryModal = ref(false);
const selectedVolumeToEditGlossary = ref<LocalVolumeMetadata>();
const submitGlossary = (
  volumeId: string,
  glossary: { [key: string]: string }
) =>
  PersonalVolumesManager.updateGlossary(volumeId, toRaw(glossary))
    .then(() => message.success('术语表提交成功'))
    .catch((error) => message.error(`术语表提交失败：${error}`))
    .then(() => {});
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
            label="加载"
            size="tiny"
            secondary
            @click="emit('volumeLoaded', volume.id)"
          />

          <c-button
            :label="`术语表[${Object.keys(volume.glossary).length}]`"
            size="tiny"
            secondary
            @click="
              () => {
                selectedVolumeToEditGlossary = volume;
                showGlossaryModal = true;
              }
            "
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

  <n-divider style="margin-bottom: 4px" />

  <c-modal title="编辑术语表" v-model:show="showGlossaryModal">
    <n-p>{{ selectedVolumeToEditGlossary?.id }}</n-p>
    <glossary-edit
      v-if="selectedVolumeToEditGlossary !== undefined"
      :glossary="selectedVolumeToEditGlossary.glossary"
    />
    <template #action>
      <c-button
        label="提交"
        type="primary"
        @click="
          submitGlossary(
            selectedVolumeToEditGlossary!!.id,
            selectedVolumeToEditGlossary!!.glossary
          )
        "
      />
    </template>
  </c-modal>
</template>
