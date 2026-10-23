<script lang="ts" setup>
import type { ChapterMeta } from '@/domain/translator/TaskState';
import type { TaskState } from '@/domain/translator/TaskState';

const props = defineProps<{
  taskState?: TaskState;
  removable?: boolean;
}>();

const emit = defineEmits<{
  preview: [chapterId: string];
  remove: [chapterId: string];
}>();

const themeVars = useThemeVars();

const chapters = computed(() => {
  const metas = props.taskState?.chapters ?? [];
  return metas.map((ch) => {
    const chState = props.taskState?.chapterStates.get(ch.chapterId);
    const liveProgress = chState?.ready
      ? { completed: chState.completedCount, total: chState.segments.length }
      : ch.segmentProgress;
    let displayStatus = ch.status;
    if (
      displayStatus === 'translating' &&
      chState?.ready &&
      chState.translatingCount === 0
    ) {
      displayStatus = 'pending';
    }
    return {
      ...ch,
      status: displayStatus,
      segmentProgress: liveProgress,
    } as ChapterMeta;
  });
});

function pct(sp: { completed: number; total: number } | undefined): number {
  if (!sp || sp.total <= 0) return 0;
  return Math.round((sp.completed / sp.total) * 100);
}
</script>

<template>
  <div class="grid">
    <div
      v-for="(ch, i) of chapters"
      :key="ch.chapterId"
      class="cell"
      :style="{ '--i': i }"
    >
      <n-button
        class="c"
        :focusable="false"
        :class="`c--${ch.status}`"
        quaternary
        @click="emit('preview', ch.chapterId)"
      >
        <span class="o">{{ ch.order }}</span>
        <span v-if="ch.segmentProgress" class="p">
          {{ ch.segmentProgress.completed }}/{{ ch.segmentProgress.total }}
        </span>

        <n-progress
          v-if="ch.segmentProgress && ch.status !== 'done'"
          type="line"
          :percentage="pct(ch.segmentProgress)"
          :height="3"
          :show-indicator="false"
          :fill-border-radius="0"
          class="bar"
        />
      </n-button>
      <n-popconfirm
        v-if="removable"
        @positive-click="emit('remove', ch.chapterId)"
      >
        <template #trigger>
          <button
            type="button"
            class="remove"
            :aria-label="`移除第 ${ch.order} 章`"
            @click.stop
          >
            ×
          </button>
        </template>
        移除本章后不再翻译；已完成章节保留基本信息，未完成草稿将清除。确定吗？
      </n-popconfirm>
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(32px, 1fr));
  gap: 2px;
}
.cell {
  position: relative;
  aspect-ratio: 1;
  animation: in 0.3s ease both;
  animation-delay: calc(var(--i, 0) * 15ms);
}
.c {
  width: 100%;
  height: 100%;
  padding: 2px;
  border: 1px solid v-bind('themeVars.borderColor');
  color: v-bind('themeVars.textColor3');
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
}
.c :deep(.n-button__content) {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  height: 100%;
}
@keyframes in {
  from {
    opacity: 0;
    transform: translateY(4px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
.c--translating {
  --pulse-from: color-mix(
    in srgb,
    v-bind('themeVars.infoColor') 0%,
    transparent
  );
  --pulse-to: color-mix(
    in srgb,
    v-bind('themeVars.infoColor') 15%,
    transparent
  );
  background: color-mix(
    in srgb,
    v-bind('themeVars.infoColor') 10%,
    transparent
  );
  color: v-bind('themeVars.infoColor');
  border-color: color-mix(
    in srgb,
    v-bind('themeVars.infoColor') 35%,
    transparent
  );
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 var(--pulse-from);
  }
  50% {
    box-shadow: 0 0 0 3px var(--pulse-to);
  }
}
.c--paused {
  background: color-mix(
    in srgb,
    v-bind('themeVars.warningColor') 10%,
    transparent
  );
  color: v-bind('themeVars.warningColor');
  border-color: color-mix(
    in srgb,
    v-bind('themeVars.warningColor') 35%,
    transparent
  );
}
.c--done {
  background: color-mix(
    in srgb,
    v-bind('themeVars.successColor') 10%,
    transparent
  );
  color: v-bind('themeVars.successColor');
  border-color: color-mix(
    in srgb,
    v-bind('themeVars.successColor') 35%,
    transparent
  );
}
.c--error {
  background: color-mix(
    in srgb,
    v-bind('themeVars.errorColor') 10%,
    transparent
  );
  color: v-bind('themeVars.errorColor');
  border-color: color-mix(
    in srgb,
    v-bind('themeVars.errorColor') 35%,
    transparent
  );
}
.o {
  position: relative;
  z-index: 1;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}
.p {
  position: relative;
  z-index: 1;
  font-size: 8px;
  line-height: 1;
  opacity: 0.75;
}
.bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
}
.remove {
  position: absolute;
  z-index: 2;
  top: -4px;
  right: -4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  padding: 0;
  border: 1px solid v-bind('themeVars.borderColor');
  border-radius: 50%;
  background: v-bind('themeVars.bodyColor');
  color: v-bind('themeVars.errorColor');
  font-size: 11px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease;
}
.cell:hover .remove,
.remove:focus-visible {
  opacity: 1;
}
@media (hover: none) {
  .remove {
    opacity: 0.65;
  }
}
</style>
