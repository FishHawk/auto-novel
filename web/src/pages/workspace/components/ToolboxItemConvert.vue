<script lang="ts" setup>
import { Epub, ParsedFile, Txt } from '@/util/file';
import { Toolbox } from './Toolbox';

const props = defineProps<{
  files: ParsedFile[];
}>();

const message = useMessage();

const convertEpubToTxt = async (epub: Epub) => {
  const name = epub.name.replace(/\.epub$/i, '.txt');
  const text = await epub.getText();
  return Txt.fromText(name, text);
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
