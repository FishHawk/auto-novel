<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed, ref, toRaw } from 'vue';

import { GlossaryService } from '@/service';
import { GenericNovelId } from '@/model/Common';
import { Glossary } from '@/model/Glossary';
import { doAction } from '@/pages/util';
import { DeleteOutlineOutlined } from '@vicons/material';

const props = defineProps<{
  gnid: GenericNovelId;
  value: Glossary;
}>();

const message = useMessage();

const glossary = ref<Glossary>({});

const showGlossaryModal = ref(false);

const toggleGlossaryModal = () => {
  if (showGlossaryModal.value === false) {
    glossary.value = { ...props.value };
  }
  showGlossaryModal.value = !showGlossaryModal.value;
};

const readableGnid = computed(() => {
  const gnid = props.gnid;
  if (gnid.type === 'web') {
    return `web/${gnid.providerId}/${gnid.novelId}`;
  } else if (gnid.type === 'wenku') {
    return `wenku/${gnid.novelId}`;
  } else {
    return `local/${gnid.volumeId}`;
  }
});

const submitGlossary = () =>
  doAction(
    GlossaryService.updateGlossary(props.gnid, toRaw(glossary.value)).then(
      () => {
        // 触发组件外的术语表本体更新。有点傻，但够用。
        for (const key in props.value) {
          delete props.value[key];
        }
        for (const key in glossary.value) {
          props.value[key] = glossary.value[key];
        }
      }
    ),
    '术语表提交',
    message
  );

const importGlossaryRaw = ref('');
const termsToAdd = ref<[string, string]>(['', '']);

const deleteTerm = (jp: string) => {
  delete glossary.value[jp];
};

const addTerm = () => {
  const [jp, zh] = termsToAdd.value;
  if (jp && zh) {
    glossary.value[jp] = zh;
    termsToAdd.value = ['', ''];
  }
};

const exportGlossary = () => {
  navigator.clipboard.writeText(
    GlossaryService.exportGlossaryToText(glossary.value)
  );
  message.info('导出成功，已经将术语表复制到剪切板');
};

const importGlossary = () => {
  const importedGlossary = GlossaryService.parseGlossaryFromText(
    importGlossaryRaw.value
  );
  if (importedGlossary === undefined) {
    message.error('导入失败：术语表格式不正确');
  } else {
    message.error('导入成功');
    for (const jp in importedGlossary) {
      const zh = importedGlossary[jp];
      glossary.value[jp] = zh;
    }
  }
};
</script>

<template>
  <c-button
    :label="`术语表[${Object.keys(value).length}]`"
    v-bind="$attrs"
    @action="toggleGlossaryModal()"
  />

  <c-modal
    title="编辑术语表"
    v-model:show="showGlossaryModal"
    :extra-height="100"
  >
    <template #header-extra>
      <n-flex
        vertical
        size="large"
        style="max-width: 400px; margin-bottom: 16px"
      >
        <n-text style="font-size: 12px">{{ readableGnid }}</n-text>
        <n-input-group>
          <n-input
            pair
            v-model:value="termsToAdd"
            size="small"
            separator="=>"
            :placeholder="['日文', '中文']"
            :input-props="{ spellcheck: false }"
          />
          <c-button
            label="添加"
            :round="false"
            size="small"
            @action="addTerm"
          />
        </n-input-group>

        <n-input-group>
          <n-input
            v-model:value="importGlossaryRaw"
            size="small"
            placeholder="批量导入术语表"
            :input-props="{ spellcheck: false }"
          />
          <c-button
            label="导出"
            :round="false"
            size="small"
            @action="exportGlossary"
          />
          <c-button
            label="导入"
            :round="false"
            size="small"
            @action="importGlossary"
          />
        </n-input-group>
      </n-flex>
    </template>

    <n-table
      v-if="Object.keys(glossary).length !== 0"
      striped
      size="small"
      style="font-size: 12px; max-width: 400px"
    >
      <tr v-for="wordJp in Object.keys(glossary).reverse()">
        <td>
          <c-button
            :icon="DeleteOutlineOutlined"
            text
            type="error"
            size="small"
            @action="deleteTerm(wordJp)"
          />
        </td>
        <td>{{ wordJp }}</td>
        <td nowrap="nowrap">=></td>
        <td>{{ glossary[wordJp] }}</td>
      </tr>
    </n-table>

    <template #action>
      <c-button label="提交" type="primary" @action="submitGlossary()" />
    </template>
  </c-modal>
</template>
