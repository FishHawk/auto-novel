<script setup lang="ts">
import { getApiErrorMessage, Toast, XConfirmDialog } from '@novelia/web-kit';

import { CommentRepo } from '@/repos';
import { useWhoamiStore } from '@/stores';

const props = defineProps<{
  userId: number;
  username: string;
  authorBlocked: boolean;
}>();
const emit = defineEmits<{ close: [] }>();
const { whoami } = storeToRefs(useWhoamiStore());
const blocked = props.authorBlocked;
const pending = ref(false);
let active = true;
onScopeDispose(() => {
  active = false;
});
watch(
  () => [props.userId, whoami.value.user?.id],
  () => emit('close'),
  { flush: 'sync' },
);

async function confirm() {
  const viewer = whoami.value.user?.id;
  if (pending.value || viewer === undefined || viewer === props.userId) return;
  pending.value = true;
  try {
    await CommentRepo.setUserBlocked(props.userId, !blocked);
    if (active && viewer === whoami.value.user?.id) {
      Toast.success(blocked ? '已取消拉黑' : '已拉黑作者');
      emit('close');
    }
  } catch (error) {
    const message = await getApiErrorMessage(
      error,
      blocked ? '取消拉黑失败' : '拉黑失败',
    );
    if (active && viewer === whoami.value.user?.id) Toast.error(message);
  } finally {
    pending.value = false;
  }
}
</script>

<template>
  <XConfirmDialog
    open
    :title="`${blocked ? '取消拉黑' : '拉黑'} @${username}`"
    :description="
      blocked
        ? '取消后，该作者的评论将重新出现在列表中。'
        : '拉黑后，评论区将过滤该作者的评论及其发起的评论串，仅对你生效。'
    "
    :confirm-label="blocked ? '取消拉黑' : '拉黑作者'"
    :loading="pending"
    :danger="!blocked"
    @update:open="
      (open) => {
        if (!open && !pending) emit('close');
      }
    "
    @confirm="confirm"
  />
</template>
