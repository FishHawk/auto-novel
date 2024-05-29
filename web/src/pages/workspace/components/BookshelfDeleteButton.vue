<script lang="ts" setup>
import { useBookshelfStore } from '../BookshelfStore';

const props = defineProps<{
  selectedIds: string[];
}>();

const message = useMessage();

const store = useBookshelfStore();

const showDeleteModal = ref(false);

const openDeleteModal = () => {
  const ids = props.selectedIds;
  if (ids.length === 0) {
    message.info('没有选中小说');
    return;
  }
  showDeleteModal.value = true;
};

const deleteSelected = async () => {
  const ids = props.selectedIds;
  const { success, failed } = await store.deleteVolumes(ids);
  message.info(`${success}本小说被删除，${failed}本失败`);
};
</script>

<template>
  <c-button
    v-bind="$attrs"
    label="删除"
    secondary
    type="error"
    @click="openDeleteModal"
  />

  <c-modal
    :title="`确定删除 ${
      selectedIds.length === 1 ? selectedIds[0] : `${selectedIds.length}本小说`
    }？`"
    v-model:show="showDeleteModal"
  >
    <template #action>
      <c-button label="确定" type="primary" @action="deleteSelected" />
    </template>
  </c-modal>
</template>
