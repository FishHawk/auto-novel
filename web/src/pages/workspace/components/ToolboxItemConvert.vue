<script lang="ts" setup>
import { Epub, ParsedFile, Txt } from '@/util/file';

const props = defineProps<{
  files: ParsedFile[];
}>();

const emit = defineEmits<{
  'update:files': [ParsedFile[]];
}>();

const convertEpubToTxt = async (epub: Epub) => {
  return new Txt(epub.name.replace(/\.epub$/i, '.txt'), await epub.getText());
};

const convertToTxt = async () => {
  const newFiles: ParsedFile[] = [];
  for (const file of props.files) {
    if (file.type === 'epub') {
      newFiles.push(await convertEpubToTxt(file));
    } else {
      newFiles.push(file);
    }
  }
  emit('update:files', newFiles);
};
</script>

<template>
  <n-flex vertical>
    <n-flex>
      <c-button label="转换" size="small" @action="convertToTxt" />
    </n-flex>
  </n-flex>
</template>
