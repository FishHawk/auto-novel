<script lang="ts" setup>
import { MoreVertOutlined, SearchOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { LocalVolumeMetadata } from '@/model/LocalVolume';
import { Setting } from '@/model/Setting';

import {
  BookshelfLocalUtil,
  useBookshelfLocalStore,
} from '@/pages/bookshelf/BookshelfLocalStore';
import { doAction } from '@/pages/util';

const props = defineProps<{
  hideTitle?: boolean;
  options?: { [key: string]: (volumes: LocalVolumeMetadata[]) => void };
  filter?: (volume: LocalVolumeMetadata) => boolean;
}>();

const emit = defineEmits<{
  volumeAdd: [File];
}>();

const message = useMessage();
const { setting } = Locator.settingRepository();

const store = useBookshelfLocalStore();
const { volumes } = storeToRefs(store);

store.loadVolumes();

const options = computed(() => {
  return [...Object.keys(props.options ?? {}), '清空文件', '批量下载'].map(
    (it) => ({ label: it, key: it }),
  );
});
const handleSelect = (key: string) => {
  switch (key) {
    case '清空文件':
      showClearModal.value = true;
      break;
    case '批量下载':
      downloadVolumes();
      break;
    default:
      props.options?.[key]?.(volumes.value ?? []);
      break;
  }
};

const downloadVolumes = async () => {
  if (sortedVolumes.value.length === 0) {
    message.info('列表为空，没有文件需要下载');
    return;
  }
  const ids = sortedVolumes.value.map((it) => it.id);
  const { success, failed } = await store.downloadVolumes(ids);
  message.info(`${success}本小说被打包，${failed}本失败`);
};

const showClearModal = ref(false);
const deleteAllVolumes = () =>
  doAction(
    store.deleteAllVolumes().then(() => (showClearModal.value = false)),
    '清空',
    message,
  );

const enableRegexMode = ref(false);
const filenameSearch = ref('');

const sortedVolumes = computed(() => {
  const filteredVolumes =
    props.filter === undefined
      ? volumes.value
      : volumes.value.filter(props.filter);
  return BookshelfLocalUtil.filterAndSortVolumes(filteredVolumes, {
    query: filenameSearch.value,
    enableRegexMode: enableRegexMode.value,
    order: setting.value.localVolumeOrder,
  });
});
</script>

<template>
  <section-header title="本地小说" v-if="!hideTitle">
    <n-flex :wrap="false">
      <bookshelf-local-add-button @done="$emit('volumeAdd', $event)" />

      <n-dropdown
        trigger="click"
        :options="options"
        :keyboard="false"
        @select="handleSelect"
      >
        <n-button circle>
          <n-icon :component="MoreVertOutlined" />
        </n-button>
      </n-dropdown>
    </n-flex>
  </section-header>

  <n-flex vertical>
    <c-action-wrapper title="搜索">
      <n-input
        clearable
        size="small"
        v-model:value="filenameSearch"
        type="text"
        placeholder="搜索文件名"
        style="max-width: 400px"
      >
        <template #suffix> <n-icon :component="SearchOutlined" /> </template>
      </n-input>

      <tag-button label="正则" v-model:checked="enableRegexMode" />
    </c-action-wrapper>

    <c-action-wrapper title="排序" align="center">
      <order-sort
        v-model:value="setting.localVolumeOrder"
        :options="Setting.localVolumeOrderOptions"
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
    <n-list style="padding-bottom: 48px; padding-right: 12px">
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
      <c-button label="确定" type="primary" @action="deleteAllVolumes" />
    </template>
  </c-modal>
</template>
