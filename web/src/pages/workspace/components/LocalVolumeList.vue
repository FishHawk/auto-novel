<script lang="ts" setup>
import { MoreVertOutlined } from '@vicons/material';
import { UploadFileInfo, useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import { PersonalVolumesManager } from '@/data/translator';
import { LocalVolumeMetadata } from '@/data/translator/db/personal';

const props = defineProps<{
  options?: { [key: string]: (volumes: LocalVolumeMetadata[]) => void };
  beforeVolumeAdd?: (file: File) => void;
}>();

const message = useMessage();

const volumes = ref<LocalVolumeMetadata[]>();

const loadVolumes = async () => {
  volumes.value = await PersonalVolumesManager.listVolumes();
};
loadVolumes();

const onFinish = ({ file }: { file: UploadFileInfo }) => {
  if (props.beforeVolumeAdd) {
    props.beforeVolumeAdd(file.file!!);
  }
  loadVolumes();
};

const options = computed(() => {
  const options =
    props.options === undefined
      ? []
      : Object.keys(props.options).map((it) => ({
          label: it,
          key: it,
        }));
  options.push({ label: '清空文件', key: '清空文件' });
  return options;
});
const handleSelect = (key: string) => {
  if (key === '清空文件') {
    showClearModal.value = true;
  } else {
    props.options?.[key]?.(volumes.value ?? []);
  }
};

const showClearModal = ref(false);
const deleteAllVolumes = () =>
  PersonalVolumesManager.deleteVolumesDb()
    .then(loadVolumes)
    .then(() => (showClearModal.value = false))
    .catch((error) => {
      message.error(`清空失败:${error}`);
    });

const order = ref<'byCreateAt' | 'byId'>('byCreateAt');
const orderOptions = [
  { value: 'byCreateAt', label: '按添加时间' },
  { value: 'byId', label: '按文件名' },
];
const sortedVolumes = computed(() => {
  if (order.value === 'byId') {
    return volumes.value?.sort((a, b) => a.id.localeCompare(b.id));
  } else {
    return volumes.value?.sort((a, b) => b.createAt - a.createAt);
  }
});

const deleteVolume = (volumeId: string) =>
  PersonalVolumesManager.deleteVolume(volumeId)
    .then(() => message.info('删除成功'))
    .then(() => loadVolumes)
    .catch((error) => message.error(`删除失败：${error}`));

defineExpose({ deleteVolume });
</script>

<template>
  <section-header title="本地小说">
    <n-flex :wrap="false">
      <add-button :show-file-list="false" @finish="onFinish" />
      <n-dropdown trigger="click" :options="options" @select="handleSelect">
        <n-button circle>
          <n-icon :component="MoreVertOutlined" />
        </n-button>
      </n-dropdown>
    </n-flex>
  </section-header>

  <n-flex vertical>
    <c-action-wrapper title="排序">
      <c-radio-group
        v-model:value="order"
        :options="orderOptions"
        size="small"
      />
    </c-action-wrapper>

    <slot name="extra" />
  </n-flex>

  <n-divider style="margin: 16px 0 8px" />

  <n-spin v-if="sortedVolumes === undefined" style="margin-top: 20px" />

  <n-empty
    v-else-if="sortedVolumes.length === 0"
    description="没有文件"
    style="margin-top: 20px"
  />

  <n-scrollbar v-else trigger="none" :size="24" style="flex: auto">
    <n-list style="padding-bottom: 48px">
      <n-list-item v-for="volume of sortedVolumes ?? []" :key="volume.id">
        <slot name="volume" v-bind="volume" />
      </n-list-item>
    </n-list>
  </n-scrollbar>

  <c-modal title="清空所有文件" v-model:show="showClearModal">
    <n-p>
      这将清空你的浏览器里面保存的所有EPUB/TXT文件，包括已经翻译的章节和术语表，无法恢复。
      你确定吗？
    </n-p>

    <template #action>
      <c-button label="确定" async type="primary" @click="deleteAllVolumes" />
    </template>
  </c-modal>
</template>
