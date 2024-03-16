<script lang="ts" setup>
import { useMessage } from 'naive-ui';

import { LocalVolumeService } from '@/data/local';
import { LocalVolumeMetadata } from '@/model/LocalVolume';
import { ref, toRaw } from 'vue';

const props = defineProps<{ volume: LocalVolumeMetadata }>();

const message = useMessage();

const glossary = ref({});

const showGlossaryModal = ref(false);

const toggleGlossaryModal = () => {
  if (showGlossaryModal.value === false) {
    glossary.value = { ...props.volume.glossary };
  }
  showGlossaryModal.value = !showGlossaryModal.value;
};

const submitGlossary = () =>
  LocalVolumeService.updateGlossary(props.volume.id, toRaw(glossary.value))
    .then(() => {
      props.volume.glossary = glossary.value;
      message.success('术语表提交成功');
    })
    .catch((error) => message.error(`术语表提交失败：${error}`))
    .then(() => {});
</script>

<template>
  <c-button
    :label="`术语表[${Object.keys(volume.glossary).length}]`"
    v-bind="$attrs"
    @action="toggleGlossaryModal()"
  />
  <c-modal title="编辑术语表" v-model:show="showGlossaryModal">
    <n-p>{{ volume.id }}</n-p>
    <glossary-edit :glossary="glossary" />
    <template #action>
      <c-button label="提交" type="primary" @action="submitGlossary()" />
    </template>
  </c-modal>
</template>
