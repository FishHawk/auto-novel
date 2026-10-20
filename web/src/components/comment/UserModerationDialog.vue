<script setup lang="ts">
import { getApiErrorMessage, Toast, XButton, XDialog } from '@novelia/web-kit';

import { useWhoamiStore } from '@/stores';
import { webKit } from '@/webKit';

const props = defineProps<{
  action: 'strike' | 'ban';
  userId: number;
  username: string;
  evidence: string;
}>();
const emit = defineEmits<{ close: [] }>();
const { whoami } = storeToRefs(useWhoamiStore());
const reason = ref('');
const point = ref(1);
const submitting = ref(false);
const isStrike = computed(() => props.action === 'strike');
const canSubmit = computed(
  () =>
    whoami.value.asAdmin &&
    whoami.value.user?.id !== props.userId &&
    reason.value.trim().length > 0 &&
    (!isStrike.value || (Number.isSafeInteger(point.value) && point.value > 0)),
);
let active = true;
onScopeDispose(() => {
  active = false;
});
watch(
  () => [whoami.value.user?.id, whoami.value.asAdmin],
  () => emit('close'),
  { flush: 'sync' },
);

async function submit() {
  if (submitting.value || !canSubmit.value) return;
  submitting.value = true;
  const viewer = whoami.value.user?.id;
  const isCurrent = () =>
    active && viewer === whoami.value.user?.id && whoami.value.asAdmin;
  try {
    if (isStrike.value) {
      await webKit.createStrike({
        username: props.username,
        reason: reason.value.trim(),
        evidence: props.evidence,
        point: point.value,
      });
    } else {
      await webKit.banUser({
        username: props.username,
        reason: reason.value.trim(),
      });
    }
    if (!isCurrent()) return;
    Toast.success(`已${isStrike.value ? '处罚' : '封禁'} @${props.username}`);
    emit('close');
  } catch (error) {
    const prefix = isStrike.value ? '处罚用户失败' : '封禁用户失败';
    const message = await getApiErrorMessage(error, prefix);
    if (isCurrent()) Toast.error(message);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <XDialog
    open
    :title="`${isStrike ? '处罚' : '封禁'} @${username}`"
    :description="
      isStrike
        ? '处罚会计入该用户的账号记录，请填写原因和处罚分值。'
        : '确认后将封禁该用户的账号，请填写封禁原因。'
    "
    :closable="!submitting"
    class="w-[28rem]"
    @update:open="
      (open) => {
        if (!open && !submitting) emit('close');
      }
    "
  >
    <form class="space-y-4" @submit.prevent="submit">
      <label class="block">
        <span class="text-sm font-medium text-ink">
          {{ isStrike ? '处罚原因' : '封禁原因' }}
        </span>
        <textarea
          v-model="reason"
          class="mt-1.5 min-h-24 w-full resize-y rounded-sm border border-border bg-surface px-3 py-2 text-sm leading-6 text-ink outline-none transition-colors placeholder:text-muted focus:border-primary focus:ring-1 focus:ring-primary disabled:opacity-50"
          :placeholder="isStrike ? '说明违规行为' : '说明封禁原因'"
          :disabled="submitting"
          required
          autofocus
        />
      </label>
      <label v-if="isStrike" class="block">
        <span class="text-sm font-medium text-ink">处罚分值</span>
        <input
          v-model.number="point"
          type="number"
          min="1"
          step="1"
          class="mt-1.5 block w-28 rounded-sm border border-border bg-surface px-3 py-2 text-sm text-ink outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary disabled:opacity-50"
          :disabled="submitting"
          required
        />
      </label>
      <div v-if="isStrike">
        <p class="text-sm font-medium text-ink">处罚依据</p>
        <p
          class="mt-1.5 max-h-28 overflow-y-auto whitespace-pre-wrap break-all rounded-sm bg-paper px-3 py-2 text-xs leading-5 text-muted"
        >
          {{ evidence }}
        </p>
      </div>
      <div class="flex justify-end gap-3 pt-2">
        <XButton
          variant="outline"
          :disabled="submitting"
          @click="emit('close')"
        >
          取消
        </XButton>
        <XButton
          type="submit"
          variant="danger"
          :disabled="!canSubmit || submitting"
        >
          {{ submitting ? '处理中…' : isStrike ? '确认处罚' : '确认封禁' }}
        </XButton>
      </div>
    </form>
  </XDialog>
</template>
