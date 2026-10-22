<script setup lang="ts">
import {
  getApiErrorMessage,
  Toast,
  XAutoComplete,
  XButton,
  XDialog,
} from '@novelia/web-kit';

import { CommentRepo } from '@/repos';
import { useWhoamiStore } from '@/stores';
import { webKit } from '@/webKit';

const props = defineProps<{
  action: 'strike' | 'ban';
  userId: number;
  username: string;
  evidence: string;
  commentId: number;
  site: string;
}>();
const emit = defineEmits<{ close: [] }>();
const { whoami } = storeToRefs(useWhoamiStore());
const reason = ref('');
const pointOptions = [1, 2, 3];
const point = ref(1);
const submitting = ref(false);
const isStrike = computed(() => props.action === 'strike');
const reasonPresets = computed(() =>
  isStrike.value ? ['骂人', '灌水', '网赚盘'] : ['营利', '宣扬纳粹'],
);
const canSubmit = computed(
  () =>
    whoami.value.asAdmin &&
    whoami.value.user?.id !== props.userId &&
    reason.value.trim().length > 0 &&
    (!isStrike.value || pointOptions.includes(point.value)),
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
  const strike = isStrike.value;
  const { username, commentId, site } = props;
  const isCurrent = () =>
    active && viewer === whoami.value.user?.id && whoami.value.asAdmin;
  try {
    if (strike) {
      await webKit.createStrike({
        username,
        reason: reason.value.trim(),
        evidence: props.evidence,
        point: point.value,
      });
    } else {
      await webKit.banUser({
        username,
        reason: reason.value.trim(),
      });
    }
    if (!isCurrent()) return;
    Toast.success(`已${strike ? '处罚' : '封禁'} @${username}`);
    try {
      if (strike) {
        try {
          await CommentRepo.deleteComment(commentId, site, true);
          if (isCurrent()) Toast.success('已删除被处罚的评论');
        } catch {
          if (isCurrent())
            Toast.error('处罚已成功，但删除评论失败，请手动删除');
        }
      }
    } finally {
      if (isCurrent()) emit('close');
    }
  } catch (error) {
    const prefix = strike ? '处罚用户失败' : '封禁用户失败';
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
        <XAutoComplete
          v-model="reason"
          :options="reasonPresets"
          size="sm"
          class="mt-1.5 bg-surface"
          :placeholder="isStrike ? '选择或填写处罚原因' : '选择或填写封禁原因'"
          empty-text="无匹配原因，可直接填写"
          :disabled="submitting"
          required
          autofocus
        />
      </label>
      <fieldset v-if="isStrike" :disabled="submitting">
        <legend class="text-sm font-medium text-ink">处罚分值</legend>
        <div class="mt-1.5 flex gap-2">
          <label v-for="option in pointOptions" :key="option">
            <input
              v-model="point"
              type="radio"
              name="strike-point"
              :value="option"
              class="peer sr-only"
            />
            <span
              class="block cursor-pointer rounded-sm border border-border bg-surface px-4 py-1.5 text-sm text-muted transition-colors peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-disabled:cursor-not-allowed peer-disabled:opacity-50"
              :class="{
                'hover:border-primary hover:text-primary peer-checked:border-primary peer-checked:bg-primary/10 peer-checked:text-primary peer-focus-visible:outline-primary':
                  option === 1,
                'hover:border-warning hover:text-warning peer-checked:border-warning peer-checked:bg-warning-soft peer-checked:text-warning peer-focus-visible:outline-warning':
                  option === 2,
                'hover:border-error hover:text-error peer-checked:border-error peer-checked:bg-error-soft peer-checked:text-error peer-focus-visible:outline-error':
                  option === 3,
              }"
            >
              {{ option }} 分
            </span>
          </label>
        </div>
      </fieldset>
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
          color="neutral"
          variant="outline"
          :disabled="submitting"
          @click="emit('close')"
        >
          取消
        </XButton>
        <XButton
          type="submit"
          color="danger"
          :disabled="!canSubmit || submitting"
        >
          {{ submitting ? '处理中…' : isStrike ? '确认处罚' : '确认封禁' }}
        </XButton>
      </div>
    </form>
  </XDialog>
</template>
