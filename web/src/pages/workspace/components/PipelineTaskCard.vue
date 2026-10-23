<script lang="ts" setup>
import { computed, reactive, ref } from 'vue';
import { Toast, XDialog } from '@novelia/web-kit';
import {
  ChevronRightOutlined,
  KeyboardDoubleArrowUpOutlined,
  KeyboardDoubleArrowDownOutlined,
  RefreshOutlined,
  DeleteOutlineOutlined,
} from '@vicons/material';
import { syncDeltaChapterProgress } from '@/domain/translator/delta/chapter-progress';
import { useDeltaSessionDetail } from '@/domain/translator/delta/useDeltaSessionDetail';
import type { DeltaSessionSummary } from '@/domain/translator/delta/session-summary';
import type { ChapterMeta } from '@/domain/translator/TaskState';
import { TaskState, ChapterSegmentState } from '@/domain/translator/TaskState';
import type { TranslateJob, TranslateJobRecord } from '@/model/Translator';
import { TranslateTaskDescriptor } from '@/model/Translator';
import { createTranslationTask } from '@/domain/translator/TranslationTask/createTranslationTask';
import type {
  TranslationTask,
  TranslatorId,
} from '@/domain/translator/TranslationTask/types';
import type { DeltaChapterSession } from '@auto-novel/translator';
import ChapterPreviewModal from './ChapterPreviewModal.vue';
import DeltaPreviewModal from './DeltaPreviewModal.vue';

const props = defineProps<{
  job: TranslateJob;
  translatorId: TranslatorId;
  taskState?: TaskState;
  taskCacheEntry?: TranslationTask;
  deltaSessions?: DeltaSessionSummary[];
  loadDeltaSession?: (id: string) => Promise<DeltaChapterSession | undefined>;
  deltaActionsDisabled?: boolean;
  deltaBusySessionIds?: ReadonlySet<string>;
  initializationError?: string;
  removable?: boolean;
}>();

const emit = defineEmits<{
  delete: [task: string];
  top: [task: string];
  bottom: [task: string];
  retry: [task: string];
  retryDelta: [task: string];
  skipReview: [sessionId: string];
  retryReview: [sessionId: string];
  restartDelta: [sessionId: string];
  removeChapter: [task: string, chapterId: string];
}>();

const taskDeltaSessions = computed(() =>
  (props.deltaSessions ?? []).filter(
    (session) => session.identity.taskId === props.job.task,
  ),
);

const hasDelta = computed(() => taskDeltaSessions.value.length > 0);

const deltaActiveCount = computed(
  () =>
    taskDeltaSessions.value.filter(
      (session) =>
        session.phase === 'translating' ||
        session.phase === 'reviewing' ||
        session.phase === 'submitting' ||
        session.phase === 'readyToSubmit',
    ).length,
);

const deltaCompletedCount = computed(
  () =>
    taskDeltaSessions.value.filter((session) => session.phase === 'completed')
      .length,
);

const deltaPausedCount = computed(
  () =>
    taskDeltaSessions.value.filter((session) => !!session.pauseReason).length,
);

const deltaTotalCacheRead = computed(() =>
  taskDeltaSessions.value.reduce(
    (sum, session) => sum + session.tokenTotals.cacheRead,
    0,
  ),
);

const deltaTotalOutput = computed(() =>
  taskDeltaSessions.value.reduce(
    (sum, session) => sum + session.tokenTotals.output,
    0,
  ),
);

const jobRecord = computed(() => props.job as TranslateJobRecord);
const localTaskState = ref<TaskState>();
const effectiveTaskState = computed(
  () => props.taskState ?? localTaskState.value,
);
const excludedChapters = computed(
  () => new Set(props.job.excludedChapters ?? []),
);
const chapterMetas = computed(() =>
  (effectiveTaskState.value?.chapters ?? []).filter(
    (chapter) => !excludedChapters.value.has(chapter.chapterId),
  ),
);

function filterExcluded(chapters: ChapterMeta[]): ChapterMeta[] {
  return chapters.filter(
    (chapter) => !excludedChapters.value.has(chapter.chapterId),
  );
}

async function getChapterMetas(): Promise<ChapterMeta[]> {
  if (effectiveTaskState.value?.initialized)
    return filterExcluded(chapterMetas.value);

  let task = props.taskCacheEntry;
  if (!task) task = createTask(props.job.task);
  if (!task.initialized) await task.initMeta();
  return filterExcluded(task.chapters);
}

function getOrCreateTaskState(): TaskState {
  if (effectiveTaskState.value) return effectiveTaskState.value;
  localTaskState.value = reactive(new TaskState(props.job.task)) as TaskState;
  return localTaskState.value as TaskState;
}

const expanded = ref(false);

const toggleExpand = async () => {
  if (expanded.value) {
    expanded.value = false;
    return;
  }
  const state = getOrCreateTaskState();
  let chapters = state.initialized ? state.chapters : await getChapterMetas();
  for (const session of taskDeltaSessions.value) {
    if (
      session.identity.taskCreatedAt === undefined ||
      session.identity.taskCreatedAt === props.job.createAt
    ) {
      chapters = syncDeltaChapterProgress(chapters, session);
    }
  }
  state.initChapters(filterExcluded(chapters));
  expanded.value = true;
};

const themeVars = useThemeVars();

const taskStatus = computed<
  'done' | 'executing' | 'pending' | 'loading' | 'error'
>(() => {
  if (props.initializationError) return 'error';
  if (chapterMetas.value.some((chapter) => chapter.status === 'translating'))
    return 'executing';
  if (
    chapterMetas.value.some(
      (chapter) =>
        chapter.status === 'pending' ||
        chapter.status === 'paused' ||
        chapter.status === 'error',
    )
  )
    return 'pending';
  if (props.job.finishAt) return 'done';
  if (!effectiveTaskState.value?.initialized) return 'loading';
  if (!chapterMetas.value.length) {
    return props.taskCacheEntry?.needsPreparation ||
      props.taskCacheEntry?.needsMetadata
      ? 'pending'
      : 'done';
  }
  return 'pending';
});

const progressLabel = computed(() => {
  if (props.initializationError) return '章节读取失败';
  if (
    !effectiveTaskState.value?.initialized &&
    !jobRecord.value.progress?.total
  )
    return '正在读取章节';
  const finished =
    jobRecord.value.progress?.finished ??
    chapterMetas.value.filter((chapter) => chapter.status === 'done').length;
  const total = jobRecord.value.progress?.total ?? chapterMetas.value.length;
  return `${finished}/${total}`;
});

function retryAllFailed() {
  for (const chapter of chapterMetas.value) {
    if (chapter.status !== 'error') continue;
    chapter.status = 'pending';
    effectiveTaskState.value?.chapterStates.delete(chapter.chapterId);
  }
  delete props.job.finishAt;
  emit('retry', props.job.task);
}

function hasFailedChapters(): boolean {
  return (
    !!props.initializationError ||
    chapterMetas.value.some((chapter) => chapter.status === 'error') ||
    deltaPausedCount.value > 0
  );
}

defineExpose({ retryAllFailed, hasFailedChapters });

function createTask(taskDesc: string): TranslationTask {
  const { desc, params } = TranslateTaskDescriptor.parse(taskDesc);
  return createTranslationTask(desc, props.translatorId, params);
}

const totalChaptersCount = computed(() => chapterMetas.value.length || 0);

const showDeltaPreview = ref(false);
const showPipelinePreview = ref(false);
const previewTitle = ref('');
const previewChapterId = ref<string | null>(null);
const pipelineChapterState = ref<ChapterSegmentState | null>(null);

const activeDeltaSummary = computed(() => {
  if (!previewChapterId.value) return null;
  return (
    taskDeltaSessions.value.find(
      (session) => session.identity.chapterId === previewChapterId.value,
    ) ?? null
  );
});

const {
  session: activeDeltaSession,
  loading: deltaDetailLoading,
  error: deltaDetailError,
  retry: retryDeltaDetail,
} = useDeltaSessionDetail({
  summary: () => activeDeltaSummary.value,
  enabled: () => showDeltaPreview.value,
  load: () => props.loadDeltaSession,
});

const openPreview = async (chapterId: string) => {
  try {
    previewChapterId.value = chapterId;
    const deltaSummary = activeDeltaSummary.value;
    if (deltaSummary) {
      previewTitle.value = deltaSummary.metadata.chapterTitle || chapterId;
      showDeltaPreview.value = true;
      return;
    }
    const chapters = await getChapterMetas();
    const meta = chapters.find((chapter) => chapter.chapterId === chapterId);
    previewTitle.value = meta?.title ?? chapterId;

    const chapterState = effectiveTaskState.value?.chapterStates.get(chapterId);
    let chapterStateForPreview: ChapterSegmentState | null = null;
    if (chapterState?.ready && !chapterState.textReleased) {
      chapterStateForPreview = chapterState;
    } else {
      let previewTask = props.taskCacheEntry;
      if (!previewTask) previewTask = createTask(props.job.task);
      if (!previewTask.initialized) await previewTask.initMeta();
      const detail = await previewTask.fetchChapter(chapterId);
      if (chapterState?.ready && !chapterState.textReleased) {
        chapterStateForPreview = chapterState;
      } else if (effectiveTaskState.value?.getStatus(chapterId) === 'done') {
        const translated = detail.oldParagraphZh;
        if (translated?.length) {
          const loadedState = new ChapterSegmentState(chapterId);
          loadedState.injectDoneTranslation(detail.paragraphs, translated);
          chapterStateForPreview = loadedState;
        }
      } else {
        chapterStateForPreview =
          chapterState && !chapterState.textReleased ? chapterState : null;
      }
    }

    pipelineChapterState.value = chapterStateForPreview;
    showPipelinePreview.value = true;
  } catch (error) {
    console.error('加载预览失败', error);
    Toast.error('加载预览失败');
  }
};

function closePipelinePreview(value: boolean) {
  showPipelinePreview.value = value;
  if (value) return;
  pipelineChapterState.value?.releaseText();
  pipelineChapterState.value = null;
}

function removeChapter(chapterId: string) {
  emit('removeChapter', props.job.task, chapterId);
}
</script>

<template>
  <n-card
    size="small"
    :hoverable="true"
    :class="{ 'task-card--expanded': expanded }"
    :header-style="{
      cursor: 'pointer',
      padding: '10px 14px',
      'user-select': 'none',
    }"
    :content-style="
      expanded ? { padding: '0 14px 10px 14px' } : { padding: '0' }
    "
  >
    <template #header>
      <div @click="toggleExpand">
        <n-flex align="center" :wrap="false" style="flex: 1; min-width: 0">
          <div style="flex: 1; min-width: 0">
            <div class="task-name">{{ job.description }}</div>
            <job-task-link :task="job.task" class="task-link" />
          </div>
          <n-flex :size="6" align="center" :wrap="false" style="flex-shrink: 0">
            <n-tag
              v-if="hasDelta"
              size="tiny"
              :bordered="false"
              :type="deltaActiveCount > 0 ? 'info' : 'primary'"
              class="delta-badge"
            >
              Delta{{ deltaActiveCount ? ` · ${deltaActiveCount} 进行中` : '' }}
            </n-tag>
            <n-tag v-if="taskStatus === 'executing'" size="tiny" type="info">
              翻译中
            </n-tag>
            <n-tag v-else-if="taskStatus === 'done'" size="tiny" type="success">
              已完成
            </n-tag>
            <n-tag v-else-if="taskStatus === 'error'" size="tiny" type="error">
              读取失败
            </n-tag>
            <n-tag v-else-if="taskStatus === 'loading'" size="tiny" type="info">
              读取中
            </n-tag>
            <n-tag v-else size="tiny" type="default">等待中</n-tag>
            <span class="task-progress">{{ progressLabel }}</span>
          </n-flex>

          <n-flex :size="4" :wrap="false" style="flex-shrink: 0">
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button
                  size="tiny"
                  circle
                  quaternary
                  @click.stop="emit('top', job.task)"
                >
                  <template #icon>
                    <n-icon :component="KeyboardDoubleArrowUpOutlined" />
                  </template>
                </n-button>
              </template>
              置顶
            </n-tooltip>
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button
                  size="tiny"
                  circle
                  quaternary
                  @click.stop="emit('bottom', job.task)"
                >
                  <template #icon>
                    <n-icon :component="KeyboardDoubleArrowDownOutlined" />
                  </template>
                </n-button>
              </template>
              置底
            </n-tooltip>
            <n-tooltip v-if="hasFailedChapters()" trigger="hover">
              <template #trigger>
                <n-button
                  size="tiny"
                  circle
                  quaternary
                  type="warning"
                  @click.stop="retryAllFailed()"
                >
                  <template #icon>
                    <n-icon :component="RefreshOutlined" />
                  </template>
                </n-button>
              </template>
              重试失败
            </n-tooltip>
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button
                  size="tiny"
                  circle
                  quaternary
                  type="error"
                  @click.stop="emit('delete', job.task)"
                >
                  <template #icon>
                    <n-icon :component="DeleteOutlineOutlined" />
                  </template>
                </n-button>
              </template>
              删除
            </n-tooltip>
          </n-flex>

          <n-icon
            :component="ChevronRightOutlined"
            :class="['expand-arrow', { 'expand-arrow--rotated': expanded }]"
          />
        </n-flex>
      </div>
    </template>

    <n-alert
      v-if="initializationError"
      type="error"
      :show-icon="false"
      style="margin: 8px 14px; width: auto"
    >
      {{ initializationError }}。修正后点击重试。
    </n-alert>

    <div v-if="expanded && chapterMetas.length">
      <div v-if="hasDelta" class="delta-card-strip">
        <n-flex align="center" justify="space-between" :wrap="false">
          <n-flex align="center" :size="6">
            <span class="delta-strip-label">Delta 进度:</span>
            <n-tag size="tiny" :bordered="false" type="success">
              完成 {{ deltaCompletedCount }}/{{ totalChaptersCount }}
            </n-tag>
            <n-tag
              v-if="deltaActiveCount"
              size="tiny"
              :bordered="false"
              type="info"
            >
              进行中 {{ deltaActiveCount }}
            </n-tag>
            <n-tag
              v-if="deltaPausedCount"
              size="tiny"
              :bordered="false"
              type="warning"
            >
              暂停 {{ deltaPausedCount }}
            </n-tag>
          </n-flex>
          <n-flex align="center" :size="6">
            <n-text depth="3" class="delta-strip-metric">
              读取缓存: {{ deltaTotalCacheRead.toLocaleString() }} · 输出:
              {{ deltaTotalOutput.toLocaleString() }}
            </n-text>
            <c-button
              v-if="deltaPausedCount"
              label="重试失败"
              size="tiny"
              secondary
              type="warning"
              @action="emit('retryDelta', job.task)"
            />
          </n-flex>
        </n-flex>
      </div>

      <chapter-grid
        :task-state="effectiveTaskState"
        :removable="removable"
        @preview="(chapterId: string) => openPreview(chapterId)"
        @remove="removeChapter"
      />
    </div>
  </n-card>

  <XDialog
    :open="showDeltaPreview && !activeDeltaSession"
    :title="previewTitle"
    @update:open="showDeltaPreview = $event"
  >
    <n-text v-if="deltaDetailLoading">正在读取 Delta 检查点…</n-text>
    <n-flex v-else vertical :size="12">
      <n-alert type="error" :show-icon="false">
        {{ deltaDetailError || 'Delta 检查点不可用' }}
      </n-alert>
      <c-button label="重试读取" @action="retryDeltaDetail" />
    </n-flex>
  </XDialog>

  <delta-preview-modal
    v-if="showDeltaPreview && activeDeltaSession"
    :show="showDeltaPreview && !!activeDeltaSession"
    :title="previewTitle"
    :session="activeDeltaSession"
    :detail-error="deltaDetailError"
    :detail-loading="deltaDetailLoading"
    :actions-disabled="
      deltaActionsDisabled || deltaBusySessionIds?.has(activeDeltaSession.id)
    "
    @retry-detail="retryDeltaDetail"
    @update:show="(value: boolean) => (showDeltaPreview = value)"
    @skip-review="(sessionId: string) => emit('skipReview', sessionId)"
    @retry-review="(sessionId: string) => emit('retryReview', sessionId)"
    @restart-delta="(sessionId: string) => emit('restartDelta', sessionId)"
  />

  <chapter-preview-modal
    :show="showPipelinePreview"
    :title="previewTitle"
    :chapter-state="pipelineChapterState"
    @update:show="closePipelinePreview"
  />
</template>

<style scoped>
.task-card--expanded {
  --n-border-color: v-bind('themeVars.primaryColorHover');
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.07);
}
.task-name {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-link {
  font-size: 11px;
  line-height: 1.2;
}
.delta-badge {
  font-weight: 500;
  letter-spacing: 0.3px;
}
.delta-card-strip {
  margin-bottom: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(128, 128, 128, 0.06);
}
.delta-strip-label {
  font-size: 11px;
  font-weight: 500;
  opacity: 0.8;
}
.delta-strip-metric {
  font-size: 11px;
}
.task-progress {
  font-size: 12px;
  color: v-bind('themeVars.textColor3');
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.expand-arrow {
  font-size: 14px;
  color: v-bind('themeVars.textColor3');
  margin-left: 2px;
  transition: transform 0.2s ease;
}
.expand-arrow--rotated {
  transform: rotate(90deg);
}
</style>
