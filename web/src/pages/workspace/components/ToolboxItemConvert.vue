<script lang="ts" setup>
import { Epub, ParsedFile, StandardNovel } from '@/util/file';
import { Toolbox } from './Toolbox';

const props = defineProps<{
  files: ParsedFile[];
}>();

const message = useMessage();

const convertEpubToTxt = async (epub: Epub) => {
  const novel = StandardNovel.fromEpub(epub);
  return await StandardNovel.toTxt(novel);
};

const convertAll = () =>
  Toolbox.convertFiles(
    props.files.filter((file) => file.type === 'epub'),
    convertEpubToTxt,
    (e) => message.error(`发生错误：${e}`),
  );
</script>

<template>
  <n-flex vertical>
    <n-flex>
      <c-button label="转换" size="small" @action="convertAll" />
    </n-flex>
  </n-flex>
</template>
