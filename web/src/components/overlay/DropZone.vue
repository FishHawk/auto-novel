<script setup lang="ts">
import { XDropZone } from '@novelia/web-kit';
import { useEventListener } from '@vueuse/core';

import { readDroppedEntry } from './readDroppedEntry';

// 仅负责拖放和目录展开；校验、上传由调用方统一处理。
defineOptions({ inheritAttrs: false });
const emit = defineEmits<{
  drop: [File[]];
  error: [unknown];
}>();

const directoryDrops = new WeakSet<DragEvent>();

// useFileDrop 只读取 DataTransfer.files，且文件列表为空时不会发出 drop。
// 捕获阶段同步保留目录项，也支持仅拖入目录的情况，避免静默遗漏目录内容。
useEventListener(
  document,
  'drop',
  (event: DragEvent) => {
    const items = Array.from(event.dataTransfer?.items ?? [])
      .filter((item) => item.kind === 'file')
      .map((item) => ({
        entry: item.webkitGetAsEntry?.(),
        file: item.getAsFile(),
      }));
    if (!items.some(({ entry }) => entry?.isDirectory)) return;
    event.preventDefault();
    directoryDrops.add(event);
    void Promise.all(
      items.map(({ entry, file }) =>
        entry ? readDroppedEntry(entry) : Promise.resolve(file ? [file] : []),
      ),
    )
      .then((files) => emit('drop', files.flat()))
      .catch((error: unknown) => emit('error', error));
  },
  { capture: true },
);

const handleDrop = (files: File[], event: DragEvent) => {
  if (!directoryDrops.has(event)) emit('drop', files);
};
</script>

<template>
  <XDropZone class="pointer-events-auto" @drop="handleDrop">
    <slot>拖拽文件到这里上传</slot>
  </XDropZone>
</template>
