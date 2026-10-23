<script lang="ts" setup>
import type {
  DeltaChapterSession,
  DeltaUsagePresence,
  DeltaChapterContext,
  DeltaReviewCategory,
} from '@auto-novel/translator';

import { isBlockingDeltaReviewIssue } from '@auto-novel/translator';
import { XDialog } from '@novelia/web-kit';

const themeVars = useThemeVars();

const props = defineProps<{
  show: boolean;
  title: string;
  session: DeltaChapterSession | null;
  actionsDisabled?: boolean;
  detailError?: string | null;
  detailLoading?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:show', val: boolean): void;
  (e: 'skipReview', sessionId: string): void;
  (e: 'retryReview', sessionId: string): void;
  (e: 'restartDelta', sessionId: string): void;
  (e: 'retryDetail'): void;
}>();

const detailRefreshWarning = computed(() =>
  props.session && props.detailError
    ? `详情更新失败，当前显示上次读取的检查点：${props.detailError}`
    : null,
);
function retryDetail() {
  if (!props.detailLoading) emit('retryDetail');
}

// -1 = 全章, 0..N-1 = 具体批次
const selectedBatchIndex = ref(-1);
const visibleBatch = computed(() => {
  const s = props.session;
  return s?.batches[
    selectedBatchIndex.value < 0 ? s.cursor : selectedBatchIndex.value
  ];
});
const includesBatchHistory = (batchId: string, requestBatchId: string) =>
  batchId === requestBatchId || batchId.startsWith(`${requestBatchId}.`);

const hasSplitHistory = computed(() => {
  const s = props.session;
  if (!s || selectedBatchIndex.value < 0) return false;
  const batch = s.batches[selectedBatchIndex.value];
  if (!batch) return false;
  return (s.requests ?? []).some(
    (r) => r.batchId !== batch.id && includesBatchHistory(batch.id, r.batchId),
  );
});

const metricsLabel = computed(() => {
  if (selectedBatchIndex.value < 0) return '全章累计';
  return hasSplitHistory.value ? '当前批次（含拆分前）' : '当前批次';
});

const onUpdateShow = (val: boolean) => {
  if (!val) {
    selectedBatchIndex.value = -1;
  }
  emit('update:show', val);
};

interface ThinkingEntry {
  attempt: number;
  text: string;
}

// 提取各个批次的思考过程 (按尝试轮次细分)
const batchThinkingList = computed(() => {
  const s = props.session;
  if (!s) return new Map<string, ThinkingEntry[]>();
  const map = new Map<string, ThinkingEntry[]>();
  const attemptMap = new Map<string, number>();

  let currentBatchId = '';
  for (const message of s.messages) {
    if (message.role === 'user') {
      const content =
        typeof message.content === 'string'
          ? message.content
          : Array.isArray(message.content)
            ? message.content
                .map((b) => (b.type === 'text' ? b.text : ''))
                .join('')
            : '';
      const match = content.match(/<current_batch\s+id="([^"]+)">/);
      if (match) {
        currentBatchId = match[1];
      }
    } else if (message.role === 'assistant') {
      let bId = currentBatchId;
      for (const block of message.content) {
        if (
          block.type === 'toolCall' &&
          block.arguments &&
          typeof block.arguments === 'object' &&
          'batchId' in block.arguments
        ) {
          bId = String((block.arguments as Record<string, unknown>).batchId);
        }
      }
      const nextAttempt = (attemptMap.get(bId) ?? 0) + 1;
      attemptMap.set(bId, nextAttempt);

      for (const block of message.content) {
        const legacyThinking = (block as { thinking?: unknown }).thinking;
        const text =
          block.type === 'thinking'
            ? block.thinking
            : typeof legacyThinking === 'string'
              ? legacyThinking
              : '';
        if (text) {
          if (!map.has(bId)) map.set(bId, []);
          map.get(bId)!.push({ attempt: nextAttempt, text });
        }
      }
    }
  }
  return map;
});

const formatBatchThinking = (
  entries: ThinkingEntry[],
  hasMultipleAttempts: boolean,
) => {
  if (!entries.length) return '';
  if (entries.length === 1 && !hasMultipleAttempts) {
    return entries[0].text;
  }
  return entries
    .map((e) => {
      const title =
        e.attempt === 1
          ? '【第 1 次尝试思考】'
          : `【第 ${e.attempt} 次尝试思考（重试修复）】`;
      return `${title}\n${e.text}`;
    })
    .join('\n\n');
};

// 当前展示的思考过程
const currentThinking = computed(() => {
  const s = props.session;
  if (!s) return '';
  const map = batchThinkingList.value;

  if (selectedBatchIndex.value === -1) {
    // 全章：汇总所有批次的思考
    const parts: string[] = [];
    for (const [batchId, entries] of map) {
      if (entries.length) {
        const reqs = (s.requests ?? []).filter((r) => r.batchId === batchId);
        const formatted = formatBatchThinking(entries, reqs.length > 1);
        parts.push(`【批次 ${batchId}】\n${formatted}`);
      }
    }
    return parts.join('\n\n--------------------\n\n');
  } else {
    // 单批次：只显示该批次的思考
    const batch = s.batches[selectedBatchIndex.value];
    if (!batch) return '';
    return [...map]
      .filter(([id]) => includesBatchHistory(batch.id, id))
      .map(([id, entries]) => {
        const reqs = s.requests.filter((r) => r.batchId === id);
        return `【批次 ${id}${id === batch.id ? '' : ' · 拆分前'}】\n${formatBatchThinking(entries, reqs.length > 1)}`;
      })
      .join('\n\n');
  }
});

// 筛选请求指标数据
const activeMetrics = computed(() => {
  const s = props.session;
  if (!s) {
    return { input: 0, cacheRead: 0, output: 0, isBatch: false };
  }
  const requests = s.requests ?? [];
  let targetRequests = requests;
  let isBatch = false;

  if (selectedBatchIndex.value >= 0) {
    const batch = s.batches[selectedBatchIndex.value];
    if (batch) {
      targetRequests = requests.filter((r) =>
        includesBatchHistory(batch.id, r.batchId),
      );
      isBatch = true;
    }
  }

  const input = targetRequests.reduce(
    (sum, r) => sum + (r.usage?.input ?? 0),
    0,
  );
  const cacheRead = targetRequests.reduce(
    (sum, r) => sum + (r.usage?.cacheRead ?? 0),
    0,
  );
  const output = targetRequests.reduce(
    (sum, r) => sum + (r.usage?.output ?? 0),
    0,
  );

  return { input, cacheRead, output, isBatch };
});

// 重试统计与一次过判定
const batchAttemptInfo = computed(() => {
  const s = props.session;
  if (!s) return null;
  if (selectedBatchIndex.value === -1) {
    let onePassCount = 0;
    let retryBatchCount = 0;
    const retryBatchIds: string[] = [];
    let degradedBatchCount = 0;
    const degradedBatchIds: string[] = [];
    for (const id of new Set(s.requests.map((r) => r.batchId))) {
      const reqs = s.requests.filter((r) => r.batchId === id);
      if (reqs.some((r) => r.degraded)) {
        degradedBatchCount++;
        degradedBatchIds.push('第' + id.replace(/^b0*/, '') + '批');
      }
      if (reqs.length === 1 && reqs[0].status === 'accepted') {
        onePassCount++;
      } else if (reqs.length > 1) {
        retryBatchCount++;
        retryBatchIds.push('第' + id.replace(/^b0*/, '') + '批');
      }
    }
    return {
      isBatch: false as const,
      onePassCount,
      retryBatchCount,
      retryBatchIds,
      degradedBatchCount,
      degradedBatchIds,
    };
  } else {
    const batch = s.batches[selectedBatchIndex.value];
    if (!batch) return null;
    const reqs = (s.requests ?? []).filter((r) =>
      includesBatchHistory(batch.id, r.batchId),
    );
    const count = reqs.length;
    const last = reqs[reqs.length - 1];
    const isOnePass = count === 1 && last?.status === 'accepted';
    return {
      isBatch: true as const,
      batchIndex: selectedBatchIndex.value + 1,
      batchId: batch.id,
      requestCount: count,
      isOnePass,
      requests: reqs,
      degradedCount: reqs.filter((r) => r.degraded).length,
    };
  }
});

// 段落列表计算
const displayParagraphs = computed(() => {
  const s = props.session;
  if (!s) return [];
  const batches = s.batches ?? [];
  const allParagraphs = s.paragraphs ?? [];
  let activeParagraphs = allParagraphs;

  if (selectedBatchIndex.value >= 0 && batches[selectedBatchIndex.value]) {
    const idSet = new Set(batches[selectedBatchIndex.value].paragraphIds);
    activeParagraphs = allParagraphs.filter((p) => idSet.has(p.id));
  }

  const currentBatchParaIds = new Set(batches[s.cursor]?.paragraphIds ?? []);

  const list: {
    id: string;
    jp: string;
    zh: string;
    status: 'done' | 'translating' | 'pending' | 'error';
  }[] = [];
  for (let idx = 0; idx < activeParagraphs.length; idx++) {
    const p = activeParagraphs[idx];
    if (p.empty) {
      list.push({ id: p.id, jp: '', zh: '', status: 'pending' });
      continue;
    }
    const zh =
      s.translations[p.id] ??
      (s.finalParagraphs && selectedBatchIndex.value === -1
        ? s.finalParagraphs[idx] ?? ''
        : '');

    let status: 'done' | 'translating' | 'pending' | 'error' = 'pending';
    if (zh) {
      status = 'done';
    } else if (currentBatchParaIds.has(p.id)) {
      status = s.pauseReason ? 'error' : 'translating';
    }

    list.push({ id: p.id, jp: p.source, zh, status });
  }
  return list;
});

const batchBtnType = (
  index: number,
): 'default' | 'success' | 'warning' | 'error' | 'info' => {
  const s = props.session;
  if (!s) return 'default';
  if (s.phase === 'completed') return 'success';
  if (index < s.cursor) return 'success';
  if (index === s.cursor) return s.pauseReason ? 'error' : 'info';
  return 'default';
};

const doneCount = computed(() => {
  return props.session ? Object.keys(props.session.translations).length : 0;
});
const totalCount = computed(() => {
  return props.session
    ? props.session.paragraphs.filter((p) => !p.empty).length
    : 0;
});

const reviewThinking = computed(() => {
  const review = props.session?.review;
  return (review?.messages.slice(review.initialMessageCount) ?? [])
    .flatMap((message) =>
      message.role === 'assistant'
        ? message.content.flatMap((block) =>
            block.type === 'thinking' && block.thinking ? [block.thinking] : [],
          )
        : [],
    )
    .join('\n\n');
});

const reviewMetrics = computed(() => {
  const requests = props.session?.review?.requests ?? [];
  const metrics: { key: keyof DeltaUsagePresence; label: string }[] = [
    { key: 'input', label: '非缓存输入' },
    { key: 'cacheRead', label: '缓存输入' },
    { key: 'cacheWrite', label: '缓存写入' },
    { key: 'output', label: '输出' },
  ];
  return metrics.map(({ key, label }) => {
    const reported = requests.filter(
      (request) => request.usagePresence?.[key] && request.usage,
    );
    const total = reported.reduce(
      (sum, request) => sum + request.usage![key],
      0,
    );
    const value = reported.length ? total.toLocaleString() : '未返回';
    return {
      key,
      label,
      value: `${value}（覆盖 ${reported.length}/${requests.length} 请求）`,
    };
  });
});

const reviewSummary = computed(() => {
  const review = props.session?.review;
  if (!review) return '未开启';
  if (review.skipped) return '已跳过';
  const unresolved = review.unresolved?.length ?? 0;
  if (unresolved && review.unresolved?.some(isBlockingDeltaReviewIssue))
    return `未解决 ${unresolved} 项`;
  if (review.completed)
    return `已完成 · 修改 ${review.changes?.length ?? 0} 段`;
  return props.session?.pauseReason ? '已暂停' : '审校中';
});

const canSkipReview = computed(
  () =>
    !props.actionsDisabled &&
    props.session?.phase === 'reviewing' &&
    !!props.session.pauseReason,
);
const canRetryReview = computed(
  () => canSkipReview.value && !props.session?.review?.completed,
);
function retryReview() {
  if (canRetryReview.value && props.session)
    emit('retryReview', props.session.id);
}
function skipReview() {
  if (canSkipReview.value && props.session)
    emit('skipReview', props.session.id);
}
function restartDelta() {
  if (!props.actionsDisabled && props.session)
    emit('restartDelta', props.session.id);
}

// --- 审校样式与交互增强 ---
const highlightedParaId = ref<string | null>(null);
let highlightTimer: ReturnType<typeof setTimeout> | null = null;

function scrollToParagraph(paraId: string) {
  const s = props.session;
  if (s && selectedBatchIndex.value >= 0) {
    const currentBatch = s.batches[selectedBatchIndex.value];
    if (currentBatch && !currentBatch.paragraphIds.includes(paraId)) {
      selectedBatchIndex.value = -1;
    }
  }

  nextTick(() => {
    highlightedParaId.value = paraId;
    if (highlightTimer) clearTimeout(highlightTimer);
    highlightTimer = setTimeout(() => {
      highlightedParaId.value = null;
    }, 2500);

    const el = document.getElementById(`delta-para-${paraId}`);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  });
}

function computeTextDiff(original: string, updated: string) {
  if (!original || !updated || original === updated) {
    return {
      prefix: original || updated || '',
      origDiff: '',
      newDiff: '',
      suffix: '',
    };
  }
  let start = 0;
  while (
    start < original.length &&
    start < updated.length &&
    original[start] === updated[start]
  ) {
    start++;
  }
  let origEnd = original.length;
  let newEnd = updated.length;
  while (
    origEnd > start &&
    newEnd > start &&
    original[origEnd - 1] === updated[newEnd - 1]
  ) {
    origEnd--;
    newEnd--;
  }
  return {
    prefix: original.slice(0, start),
    origDiff: original.slice(start, origEnd),
    newDiff: updated.slice(start, newEnd),
    suffix: original.slice(origEnd),
  };
}

const reviewCategoryMap: Record<
  DeltaReviewCategory,
  {
    label: string;
    type: 'info' | 'warning' | 'success' | 'primary' | 'default';
  }
> = {
  terminology: { label: '术语一致', type: 'primary' },
  accuracy: { label: '准确性', type: 'info' },
  fluency: { label: '润色通顺', type: 'success' },
  omission: { label: '补全漏译', type: 'warning' },
  format: { label: '格式排版', type: 'default' },
};

function getCategoryInfo(category: string) {
  return (
    reviewCategoryMap[category as DeltaReviewCategory] ?? {
      label: category,
      type: 'default' as const,
    }
  );
}

const computedReviewChanges = computed(() => {
  const review = props.session?.review;
  if (!review?.changes) return [];
  const origMap = review.originalTranslations ?? {};
  return review.changes.map((change) => {
    const originalText = origMap[change.paragraphId] ?? '';
    const diff = computeTextDiff(originalText, change.translation);
    const cat = getCategoryInfo(change.category);
    return {
      ...change,
      originalText,
      diff,
      categoryLabel: cat.label,
      categoryType: cat.type,
    };
  });
});

const reviewStatusBadge = computed(() => {
  const review = props.session?.review;
  if (!review)
    return { text: '未开启', type: 'default' as const, spinning: false };
  if (review.skipped)
    return { text: '已跳过', type: 'default' as const, spinning: false };
  if (props.session?.pauseReason)
    return { text: '审校暂停', type: 'error' as const, spinning: false };
  if (review.completed) {
    const changesCount = review.changes?.length ?? 0;
    const unresolvedCount = review.unresolved?.length ?? 0;
    if (unresolvedCount > 0) {
      return {
        text: `已完成 · 待确认 ${unresolvedCount} 项`,
        type: 'warning' as const,
        spinning: false,
      };
    }
    if (changesCount > 0) {
      return {
        text: `已完成 · 修改 ${changesCount} 段`,
        type: 'success' as const,
        spinning: false,
      };
    }
    return {
      text: '已完成 · 原文无需修改',
      type: 'success' as const,
      spinning: false,
    };
  }
  return {
    text: '审校中',
    type: 'info' as const,
    spinning: true,
  };
});

const formattedReviewMetrics = computed(() => {
  const requests = props.session?.review?.requests ?? [];
  return reviewMetrics.value.map((m) => {
    const key = m.key as keyof DeltaUsagePresence;
    const reported = requests.filter((r) => r.usagePresence?.[key] && r.usage);
    const total = reported.reduce((sum, r) => sum + r.usage![key], 0);
    const hasValue = reported.length > 0;
    let tagType: 'default' | 'success' | 'info' = 'default';
    if (key === 'cacheRead' && total > 0) tagType = 'success';
    else if (key === 'output' && total > 0) tagType = 'info';

    return {
      ...m,
      displayValue: hasValue ? total.toLocaleString() : '-',
      coverageText: `覆盖 ${reported.length}/${requests.length} 请求`,
      tagType,
    };
  });
});

interface ReviewToolCallItem {
  id: string;
  name: string;
  offset?: number;
  offsetLabel?: string;
  status: 'success' | 'warning' | 'error' | 'pending';
  statusText: string;
  statusTagType: 'success' | 'warning' | 'error' | 'info';
  targetChapter?: string;
  sourceCount?: number;
  translationCount?: number;
  reason?: string;
  rawArgs?: Record<string, unknown>;
  rawResult?: string;
}

const reviewToolCalls = computed<ReviewToolCallItem[]>(() => {
  const review = props.session?.review;
  if (!review) return [];
  const reviewMessages = review.messages.slice(review.initialMessageCount ?? 0);

  const toolResults = new Map<object, { text: string; isError?: boolean }>();
  const pendingCalls = new Map<string, object>();
  for (const msg of reviewMessages) {
    if (msg.role === 'assistant') {
      pendingCalls.clear();
      for (const block of msg.content)
        if (block.type === 'toolCall') pendingCalls.set(block.id, block);
    }
    if (msg.role === 'toolResult' && msg.toolCallId) {
      const text = Array.isArray(msg.content)
        ? msg.content
            .map((block) => {
              if (typeof block === 'string') return block;
              const part = block as { type?: string; text?: string };
              return part.type === 'text' ? part.text ?? '' : '';
            })
            .join('')
        : typeof msg.content === 'string'
          ? msg.content
          : '';
      const call = pendingCalls.get(msg.toolCallId);
      if (call) toolResults.set(call, { text, isError: !!msg.isError });
    }
  }

  const calls: ReviewToolCallItem[] = [];

  for (const [messageIndex, msg] of reviewMessages.entries()) {
    if (msg.role === 'assistant' && Array.isArray(msg.content)) {
      for (const [blockIndex, block] of msg.content.entries()) {
        if (block.type === 'toolCall') {
          const callId = JSON.stringify([messageIndex, blockIndex, block.id]);
          const name = block.name;
          const args = (block.arguments ?? {}) as Record<string, unknown>;
          const res = toolResults.get(block);

          if (name === 'get_chapter_context') {
            const offset =
              typeof args.offset === 'number' ? args.offset : undefined;
            const offsetLabel =
              offset === -1
                ? '前 1 章'
                : offset === 1
                  ? '后 1 章'
                  : offset === -2
                    ? '前 2 章'
                    : offset === 2
                      ? '后 2 章'
                      : `偏移 ${offset}`;

            if (!res) {
              calls.push({
                id: callId,
                name,
                offset,
                offsetLabel,
                status: 'pending',
                statusText: '读取中...',
                statusTagType: 'info',
                rawArgs: args,
              });
              continue;
            }

            if (res.isError) {
              calls.push({
                id: callId,
                name,
                offset,
                offsetLabel,
                status: 'error',
                statusText: '执行错误',
                statusTagType: 'error',
                reason: res.text,
                rawArgs: args,
                rawResult: res.text,
              });
              continue;
            }

            try {
              const parsed = JSON.parse(res.text) as DeltaChapterContext;
              if (parsed.available) {
                calls.push({
                  id: callId,
                  name,
                  offset,
                  offsetLabel,
                  status: 'success',
                  statusText: '读取成功',
                  statusTagType: 'success',
                  targetChapter: parsed.title || parsed.chapterId,
                  sourceCount: parsed.source?.length ?? 0,
                  translationCount: parsed.translation?.length ?? 0,
                  rawArgs: args,
                  rawResult: res.text,
                });
              } else {
                calls.push({
                  id: callId,
                  name,
                  offset,
                  offsetLabel,
                  status: 'warning',
                  statusText: '不可用',
                  statusTagType: 'warning',
                  reason: parsed.reason,
                  rawArgs: args,
                  rawResult: res.text,
                });
              }
            } catch {
              calls.push({
                id: callId,
                name,
                offset,
                offsetLabel,
                status: 'error',
                statusText: '返回异常',
                statusTagType: 'error',
                reason: res.text,
                rawArgs: args,
                rawResult: res.text,
              });
            }
          } else if (name === 'submit_review') {
            const changesCount = Array.isArray(args?.changes)
              ? args.changes.length
              : 0;
            const unresolvedCount = Array.isArray(args?.unresolved)
              ? args.unresolved.length
              : 0;
            const isError = res?.isError;
            calls.push({
              id: callId,
              name,
              status: !res ? 'pending' : isError ? 'error' : 'success',
              statusText: !res
                ? '提交中...'
                : isError
                  ? '提交失败'
                  : '提交成功',
              statusTagType: !res ? 'info' : isError ? 'error' : 'success',
              reason: isError ? res?.text : undefined,
              targetChapter: `修订 ${changesCount} 段 · 未解决 ${unresolvedCount} 项`,
              rawArgs: args,
              rawResult: res?.text,
            });
          } else {
            calls.push({
              id: callId,
              name,
              status: res?.isError ? 'error' : res ? 'success' : 'pending',
              statusText: res?.isError ? '失败' : res ? '已执行' : '等待中',
              statusTagType: res?.isError ? 'error' : res ? 'success' : 'info',
              reason: res?.text,
              rawArgs: args,
              rawResult: res?.text,
            });
          }
        }
      }
    }
  }

  if (review.pendingAssistant?.message?.content) {
    for (const [
      blockIndex,
      block,
    ] of review.pendingAssistant.message.content.entries()) {
      if (block.type === 'toolCall') {
        const name = block.name;
        const args = (block.arguments ?? {}) as Record<string, unknown>;
        const offset =
          typeof args.offset === 'number' ? args.offset : undefined;
        const offsetLabel =
          offset === -1
            ? '前 1 章'
            : offset === 1
              ? '后 1 章'
              : `偏移 ${offset}`;
        calls.push({
          id: JSON.stringify([
            'pending',
            review.pendingAssistant.requestId,
            blockIndex,
            block.id,
          ]),
          name,
          offset,
          offsetLabel,
          status: 'pending',
          statusText: '结算中...',
          statusTagType: 'info',
          rawArgs: args,
        });
      }
    }
  }

  return calls;
});

const defaultExpandedReviewPanels = computed(() => {
  const s = props.session;
  if (!s?.review) return [];
  const list: string[] = [];
  if (s.review.changes?.length) {
    list.push('review-changes');
  }
  if (
    reviewToolCalls.value.some(
      (c) => c.status === 'warning' || c.status === 'error',
    )
  ) {
    list.push('review-tools');
  }
  return list;
});
</script>

<template>
  <XDialog
    :open="show"
    @update:open="onUpdateShow"
    :title="title"
    style="width: min(920px, 95vw)"
  >
    <div v-if="session" class="delta-modal-header-extra">
      <div class="delta-header-stats">
        <n-tag size="small" :bordered="false" round type="primary">Delta</n-tag>
        <n-tag size="small" :bordered="false">
          共 {{ totalCount }} 段 · {{ session.batches.length }} 批
        </n-tag>
        <n-tag size="small" :bordered="false" type="success">
          ✓ {{ doneCount }}
        </n-tag>
        <n-tag
          v-if="session.phase === 'translating'"
          size="small"
          :bordered="false"
          type="info"
        >
          ● 第 {{ session.cursor + 1 }}/{{ session.batches.length }} 批进行中
        </n-tag>
        <n-tag
          v-else-if="session.phase === 'reviewing'"
          size="small"
          :bordered="false"
          type="info"
        >
          审校中
        </n-tag>
        <n-tag
          v-else-if="session.phase === 'completed'"
          size="small"
          :bordered="false"
          type="success"
        >
          ✓ 全章完成
        </n-tag>
        <n-tag
          v-if="session.pauseReason"
          size="small"
          :bordered="false"
          type="error"
        >
          ✕ 暂停
        </n-tag>
      </div>
      <n-divider style="margin: 6px 0 6px" />
    </div>

    <div v-if="session" class="delta-modal-body">
      <n-alert v-if="detailRefreshWarning" type="warning" :show-icon="false">
        <n-flex vertical :size="8">
          <span>{{ detailRefreshWarning }}</span>
          <c-button
            label="重试读取"
            size="small"
            :disabled="detailLoading"
            @action="retryDetail"
          />
        </n-flex>
      </n-alert>
      <!-- 两行元数据信息：第1行翻译器与批次状态，第2行Token指标 -->
      <div class="delta-meta-container">
        <!-- 第 1 行：翻译器信息 + 一次通过 / 重试 / 拆分状态胶囊 -->
        <n-flex :size="8" align="center" class="delta-meta-row">
          <n-text depth="3" class="delta-meta-label">翻译器：</n-text>
          <n-text class="delta-meta-value">
            {{ session.model.model }} · {{ session.model.protocol }}
          </n-text>

          <!-- 一次通过 / 重试状态胶囊 -->
          <template v-if="batchAttemptInfo">
            <template v-if="batchAttemptInfo.isBatch">
              <n-tag
                v-if="batchAttemptInfo.isOnePass"
                size="tiny"
                :bordered="false"
                type="success"
              >
                ✓ 一次通过
              </n-tag>
              <n-popover
                v-else-if="batchAttemptInfo.requestCount > 1"
                trigger="hover"
                placement="bottom-start"
              >
                <template #trigger>
                  <n-tag
                    size="tiny"
                    :bordered="false"
                    type="warning"
                    style="cursor: pointer"
                  >
                    重试 {{ batchAttemptInfo.requestCount - 1 }} 次 (共
                    {{ batchAttemptInfo.requestCount }} 次请求)
                  </n-tag>
                </template>
                <div style="font-size: 12px; max-width: 380px; padding: 2px">
                  <div style="font-weight: 500; margin-bottom: 6px">
                    请求记录明细
                  </div>
                  <div
                    v-for="(r, idx) in batchAttemptInfo.requests"
                    :key="r.requestId"
                    style="
                      display: flex;
                      gap: 8px;
                      align-items: center;
                      margin-top: 4px;
                    "
                  >
                    <n-tag
                      size="tiny"
                      :bordered="false"
                      :type="r.status === 'accepted' ? 'success' : 'error'"
                    >
                      {{ r.batchId }} · 尝试 {{ idx + 1 }}
                    </n-tag>
                    <n-tag
                      v-if="r.degraded"
                      size="tiny"
                      :bordered="false"
                      type="info"
                    >
                      已降级
                    </n-tag>
                    <span
                      :style="{
                        color:
                          r.status === 'accepted'
                            ? undefined
                            : themeVars.errorColor,
                      }"
                    >
                      {{
                        r.status === 'accepted'
                          ? '✓ 校验通过'
                          : r.error || r.status
                      }}
                    </span>
                  </div>
                </div>
              </n-popover>
              <n-tag
                v-if="visibleBatch?.id.includes('.')"
                size="tiny"
                :bordered="false"
                type="info"
              >
                拆分批次 ({{ visibleBatch.paragraphIds.length }} 段)
              </n-tag>
              <n-tag
                v-if="batchAttemptInfo.degradedCount"
                size="tiny"
                :bordered="false"
                type="info"
              >
                已降级重试 {{ batchAttemptInfo.degradedCount }} 次
              </n-tag>
            </template>
            <template v-else>
              <n-tag
                v-if="
                  batchAttemptInfo.retryBatchCount === 0 &&
                  batchAttemptInfo.onePassCount > 0
                "
                size="tiny"
                :bordered="false"
                type="success"
              >
                ✓ 全部批次一次通过
              </n-tag>
              <n-tag
                v-else-if="batchAttemptInfo.retryBatchCount > 0"
                size="tiny"
                :bordered="false"
                type="warning"
              >
                {{ batchAttemptInfo.retryBatchCount }} 批存在重试 ({{
                  batchAttemptInfo.retryBatchIds.join('、')
                }})
              </n-tag>
              <n-tag
                v-if="batchAttemptInfo.degradedBatchCount"
                size="tiny"
                :bordered="false"
                type="info"
                :title="batchAttemptInfo.degradedBatchIds.join('、')"
              >
                {{ batchAttemptInfo.degradedBatchCount }} 批触发降级
              </n-tag>
            </template>
          </template>
        </n-flex>

        <!-- 第 2 行：批次Token指标 (非缓存输入 xxxx 缓存输入 xxxx 输出 xxxx) -->
        <n-flex :size="8" align="center" class="delta-meta-row">
          <n-text depth="3" class="delta-meta-label">
            初译 {{ metricsLabel }}：
          </n-text>
          <n-tag size="tiny" :bordered="false">
            非缓存输入 {{ activeMetrics.input.toLocaleString() }}
          </n-tag>
          <n-tag size="tiny" :bordered="false" type="success">
            缓存输入 {{ activeMetrics.cacheRead.toLocaleString() }}
          </n-tag>
          <n-tag size="tiny" :bordered="false" type="info">
            输出 {{ activeMetrics.output.toLocaleString() }}
          </n-tag>
        </n-flex>
      </div>

      <!-- 批次选择按钮 -->
      <div v-if="session.batches.length > 1" class="delta-batch-tabs">
        <n-button
          size="small"
          secondary
          :type="selectedBatchIndex === -1 ? 'primary' : 'default'"
          :style="
            selectedBatchIndex === -1
              ? {
                  outline: `2px solid ${themeVars.primaryColor}`,
                  outlineOffset: '1px',
                }
              : undefined
          "
          @click="selectedBatchIndex = -1"
        >
          全章
        </n-button>
        <n-button
          v-for="(batch, i) of session.batches"
          :key="batch.id"
          size="small"
          secondary
          :type="batchBtnType(i)"
          :style="
            selectedBatchIndex === i
              ? {
                  outline: `2px solid ${themeVars.primaryColor}`,
                  outlineOffset: '1px',
                }
              : undefined
          "
          @click="selectedBatchIndex = i"
        >
          {{ batch.id.replace(/^b0*/, '') }}
        </n-button>
      </div>

      <!-- 错误警告提示 (仅在会话真正暂停时呈现) -->
      <n-alert
        v-if="session.pauseReason"
        type="warning"
        :title="session.phase === 'reviewing' ? '审校暂停' : '翻译暂停'"
        :bordered="false"
        style="margin-bottom: 8px"
      >
        {{ session.pauseReason }}
      </n-alert>

      <!-- 思考过程 (无 AI 推理 badge，按批次或全章独立呈现) -->
      <n-collapse v-if="currentThinking" class="delta-thinking-collapse">
        <n-collapse-item title="初译思考 / 摘要（仅保留 3 天）" name="thinking">
          <div class="delta-thinking-content">{{ currentThinking }}</div>
        </n-collapse-item>
      </n-collapse>

      <!-- ReviewX 全章对照审校 -->
      <div v-if="session.review" class="delta-review-container">
        <!-- 头部 Header -->
        <div class="delta-review-header">
          <div class="delta-review-header-left">
            <span class="delta-review-title">ReviewX 审校</span>
            <n-tag
              size="small"
              :bordered="false"
              round
              :type="reviewStatusBadge.type"
              class="delta-review-status-tag"
            >
              <span
                v-if="reviewStatusBadge.spinning"
                class="delta-review-pulse-dot"
              />
              {{ reviewStatusBadge.text }}
            </n-tag>
            <span class="delta-review-session-mode">
              {{
                session.review.options.mode === 'append'
                  ? '追加原会话'
                  : '新开会话'
              }}
              · 已请求 {{ session.review.totalAttempts }} 次 · 重试窗口
              {{ session.review.window }}
            </span>
          </div>
        </div>

        <!-- Token 指标行 -->
        <div class="delta-review-metrics-row">
          <span class="delta-review-metrics-label">审校消耗：</span>
          <n-flex :size="6" align="center" style="display: inline-flex">
            <n-tooltip
              v-for="metric of formattedReviewMetrics"
              :key="metric.key"
              trigger="hover"
            >
              <template #trigger>
                <n-tag
                  size="tiny"
                  :bordered="false"
                  :type="metric.tagType"
                  class="delta-review-metric-tag"
                >
                  {{ metric.label }} {{ metric.displayValue }}
                </n-tag>
              </template>
              <span>
                {{ metric.label }}：{{ metric.displayValue }}（{{
                  metric.coverageText
                }}）
              </span>
            </n-tooltip>
          </n-flex>
        </div>

        <!-- 待确认项 (Unresolved) -->
        <div
          v-if="session.review.unresolved?.length"
          class="delta-review-unresolved-box"
        >
          <div class="delta-unresolved-header">
            <span class="delta-unresolved-title">
              待确认项（未解决 {{ session.review.unresolved.length }} 项）
            </span>
          </div>
          <div class="delta-unresolved-list">
            <div
              v-for="finding of session.review.unresolved"
              :key="finding.paragraphId"
              class="delta-unresolved-item"
            >
              <span
                class="delta-para-badge"
                title="点击定位到下方段落"
                @click="scrollToParagraph(finding.paragraphId)"
              >
                #{{ finding.paragraphId }}
              </span>
              <span class="delta-unresolved-reason">{{ finding.reason }}</span>
              <n-button
                text
                size="tiny"
                type="primary"
                class="delta-jump-btn"
                @click="scrollToParagraph(finding.paragraphId)"
              >
                定位
              </n-button>
            </div>
          </div>
        </div>

        <!-- 折叠面板集合 -->
        <n-collapse
          :default-expanded-names="defaultExpandedReviewPanels"
          class="delta-review-collapse"
        >
          <!-- 1. 上下文与工具调用记录 (ChatGPT 建议) -->
          <n-collapse-item name="review-tools">
            <template #header>
              <div class="delta-collapse-title-row">
                <span>上下文与工具调用</span>
                <n-tag
                  v-if="reviewToolCalls.length"
                  size="tiny"
                  :bordered="false"
                  round
                  type="info"
                  style="margin-left: 6px"
                >
                  {{ reviewToolCalls.length }} 次记录
                </n-tag>
              </div>
            </template>

            <div v-if="!reviewToolCalls.length" class="delta-tools-empty">
              本次审校直接提交，未请求相邻章节上下文。
            </div>
            <div v-else class="delta-tools-list">
              <div
                v-for="(call, idx) of reviewToolCalls"
                :key="call.id || idx"
                class="delta-tool-item"
                :class="'delta-tool-status-' + call.status"
              >
                <div class="delta-tool-header">
                  <div class="delta-tool-meta">
                    <n-tag
                      v-if="call.name === 'get_chapter_context'"
                      size="small"
                      round
                      type="info"
                      :bordered="false"
                    >
                      读相邻章 · {{ call.offsetLabel }}
                    </n-tag>
                    <n-tag
                      v-else-if="call.name === 'submit_review'"
                      size="small"
                      round
                      type="primary"
                      :bordered="false"
                    >
                      提交审校 (submit_review)
                    </n-tag>
                    <n-tag v-else size="small" round :bordered="false">
                      {{ call.name }}
                    </n-tag>

                    <n-tag
                      size="tiny"
                      :bordered="false"
                      :type="call.statusTagType"
                    >
                      {{ call.statusText }}
                    </n-tag>
                  </div>
                </div>

                <div class="delta-tool-body">
                  <!-- 成功读取相邻章 -->
                  <template
                    v-if="
                      call.name === 'get_chapter_context' &&
                      call.status === 'success'
                    "
                  >
                    <div class="delta-tool-detail">
                      <span class="delta-tool-label">目标章节：</span>
                      <span class="delta-tool-value font-medium">
                        {{ call.targetChapter }}
                      </span>
                    </div>
                    <div class="delta-tool-detail">
                      <span class="delta-tool-label">内容段数：</span>
                      <span class="delta-tool-value">
                        原文 {{ call.sourceCount }} 段
                        <template v-if="call.translationCount">
                          · 已有译文 {{ call.translationCount }} 段
                        </template>
                      </span>
                    </div>
                  </template>

                  <!-- 相邻章不可用/超出限制 -->
                  <template
                    v-else-if="
                      call.name === 'get_chapter_context' &&
                      call.status === 'warning'
                    "
                  >
                    <div class="delta-tool-detail text-amber-600">
                      <span class="delta-tool-label">不可用原因：</span>
                      <span>{{ call.reason }}</span>
                    </div>
                  </template>

                  <!-- 错误 -->
                  <template v-else-if="call.status === 'error'">
                    <div class="delta-tool-detail text-red-600">
                      <span class="delta-tool-label">失败原因：</span>
                      <span>{{ call.reason }}</span>
                    </div>
                  </template>

                  <!-- 提交审校 -->
                  <template v-else-if="call.name === 'submit_review'">
                    <div class="delta-tool-detail">
                      <span class="delta-tool-label">提交内容：</span>
                      <span>{{ call.targetChapter }}</span>
                    </div>
                    <div
                      v-if="call.reason"
                      class="delta-tool-detail text-red-600"
                    >
                      <span class="delta-tool-label">失败原因：</span>
                      <span>{{ call.reason }}</span>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </n-collapse-item>

          <!-- 2. 审校思考 / 摘要 -->
          <n-collapse-item
            v-if="reviewThinking"
            title="审校思考 / 摘要（仅保留 3 天）"
            name="review-thinking"
          >
            <div class="delta-thinking-content">{{ reviewThinking }}</div>
          </n-collapse-item>

          <!-- 3. 修订明细 (若有修改) -->
          <n-collapse-item
            v-if="session.review.changes?.length"
            name="review-changes"
          >
            <template #header>
              <div class="delta-collapse-title-row">
                <span>修订明细</span>
                <n-tag
                  size="tiny"
                  :bordered="false"
                  round
                  type="success"
                  style="margin-left: 6px"
                >
                  {{ session.review.changes.length }} 处修改
                </n-tag>
              </div>
            </template>

            <div class="delta-changes-list">
              <div
                v-for="change of computedReviewChanges"
                :key="change.paragraphId"
                class="delta-change-card"
              >
                <!-- 卡片顶栏：段落号 + 分类 Tag + 定位按钮 -->
                <div class="delta-change-card-header">
                  <div class="delta-change-card-meta">
                    <span
                      class="delta-para-badge"
                      title="点击定位到下方段落"
                      @click="scrollToParagraph(change.paragraphId)"
                    >
                      #{{ change.paragraphId }}
                    </span>
                    <n-tag
                      size="tiny"
                      :bordered="false"
                      round
                      :type="change.categoryType"
                    >
                      {{ change.categoryLabel }}
                    </n-tag>
                  </div>
                  <n-button
                    text
                    size="tiny"
                    type="primary"
                    class="delta-jump-btn"
                    @click="scrollToParagraph(change.paragraphId)"
                  >
                    定位到段落
                  </n-button>
                </div>

                <!-- Diff 对照区 -->
                <div class="delta-change-diff-box">
                  <!-- 原译行 -->
                  <div class="delta-diff-row delta-diff-orig">
                    <span class="delta-diff-badge">- 原译</span>
                    <span class="delta-diff-content">
                      <template
                        v-if="change.diff.origDiff || change.diff.newDiff"
                      >
                        {{ change.diff.prefix }}
                        <del class="delta-diff-del">
                          {{ change.diff.origDiff }}
                        </del>
                        {{ change.diff.suffix }}
                      </template>
                      <template v-else>
                        {{ change.originalText }}
                      </template>
                    </span>
                  </div>
                  <!-- 审校行 -->
                  <div class="delta-diff-row delta-diff-new">
                    <span class="delta-diff-badge">+ 审校</span>
                    <span class="delta-diff-content">
                      <template
                        v-if="change.diff.origDiff || change.diff.newDiff"
                      >
                        {{ change.diff.prefix }}
                        <ins class="delta-diff-ins">
                          {{ change.diff.newDiff }}
                        </ins>
                        {{ change.diff.suffix }}
                      </template>
                      <template v-else>
                        {{ change.translation }}
                      </template>
                    </span>
                  </div>
                </div>

                <!-- 审校依据 -->
                <div v-if="change.reason" class="delta-change-reason">
                  <span class="delta-reason-prefix">依据：</span>
                  <span class="delta-reason-text">{{ change.reason }}</span>
                </div>
              </div>
            </div>
          </n-collapse-item>
        </n-collapse>
      </div>

      <!-- 原文 vs 译文 双语对照表格 -->
      <n-table :bordered="false" size="small" class="delta-preview-table">
        <thead>
          <tr>
            <th style="width: 50%">原文</th>
            <th style="width: 50%">译文</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(p, i) of displayParagraphs"
            :key="i"
            :id="'delta-para-' + p.id"
            :class="{ 'delta-row-highlighted': highlightedParaId === p.id }"
          >
            <td class="delta-jp">{{ p.jp }}</td>
            <td class="delta-zh">
              <template v-if="p.zh">{{ p.zh }}</template>
              <span
                v-else-if="p.status === 'translating'"
                class="delta-translating"
              >
                （正在翻译...）
              </span>
              <span v-else-if="p.jp" class="delta-none">（待翻译）</span>
            </td>
          </tr>
        </tbody>
      </n-table>
    </div>
    <template v-if="session" #footer>
      <n-flex :size="8">
        <c-button
          v-if="
            session.phase === 'reviewing' &&
            session.pauseReason &&
            !session.review?.completed
          "
          label="重试失败"
          :disabled="!canRetryReview"
          @action="retryReview"
        />
        <c-button
          v-if="session.phase === 'reviewing' && session.pauseReason"
          label="知悉并通过"
          :disabled="!canSkipReview"
          @action="skipReview"
        />
        <c-button
          label="本章重新翻译"
          :disabled="actionsDisabled"
          @action="restartDelta"
        />
      </n-flex>
    </template>
  </XDialog>
</template>

<style scoped>
.delta-modal-header-extra {
  margin-top: -20px;
}
.delta-header-stats {
  display: flex;
  align-items: center;
  gap: 8px;
}
.delta-meta-container {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 4px 2px 8px;
}
.delta-meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.delta-meta-label {
  font-size: 13px;
  line-height: 1.4;
}
.delta-meta-value {
  font-size: 13px;
  line-height: 1.4;
}
.delta-batch-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 4px 2px 10px;
}
.delta-batch-tabs .n-button {
  min-width: 0;
}
.delta-thinking-collapse {
  margin: 6px 0 10px;
  background: rgba(128, 128, 128, 0.05);
  border-radius: 6px;
  padding: 0 10px;
}
.delta-thinking-content {
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  color: v-bind('themeVars.textColor2');
  max-height: 240px;
  overflow-y: auto;
  padding: 6px 0;
}
.delta-preview-table {
  margin-top: 8px;
  table-layout: fixed;
}
.delta-preview-table td {
  vertical-align: top;
  line-height: 1.7;
}

@media (min-width: 769px) {
  .delta-jp,
  .delta-zh {
    opacity: 1;
  }
}

@media (max-width: 768px) {
  .delta-preview-table thead {
    display: none;
  }
  .delta-preview-table tr {
    display: block;
    margin-bottom: 10px;
    padding-bottom: 8px;
    border-bottom: 1px solid rgba(128, 128, 128, 0.2);
  }
  .delta-preview-table td {
    display: block;
    width: 100% !important;
    padding: 3px 8px;
    border: none;
  }
  .delta-jp {
    font-weight: 500;
    opacity: 1;
  }
  .delta-zh {
    opacity: 0.6;
  }
}

.delta-none {
  opacity: 0.4;
  font-style: italic;
}
.delta-translating {
  color: v-bind('themeVars.infoColor');
  font-size: 12px;
}

/* --- ReviewX 审校看板增强样式 --- */
.delta-review-container {
  margin: 8px 0 14px;
  background: rgba(128, 128, 128, 0.04);
  border: 1px solid rgba(128, 128, 128, 0.12);
  border-radius: 8px;
  padding: 10px 14px 12px;
  transition: all 0.2s ease;
}
.delta-review-container:hover {
  border-color: rgba(128, 128, 128, 0.2);
}
.delta-review-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.delta-review-header-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.delta-review-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  color: v-bind('themeVars.textColor1');
}
.delta-review-pulse-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: currentColor;
  margin-right: 4px;
  animation: deltaPulseDot 1.4s infinite ease-in-out;
}
@keyframes deltaPulseDot {
  0%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1.2);
  }
}
.delta-review-session-mode {
  font-size: 12px;
  color: v-bind('themeVars.textColor3');
}
.delta-review-metrics-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.delta-review-metrics-label {
  font-size: 12px;
  color: v-bind('themeVars.textColor3');
}
.delta-review-metric-tag {
  cursor: default;
}
.delta-review-unresolved-box {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.25);
  border-radius: 6px;
}
.delta-unresolved-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #d97706;
  margin-bottom: 6px;
}
.delta-unresolved-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.delta-unresolved-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.delta-unresolved-reason {
  flex: 1;
  color: v-bind('themeVars.textColor2');
  overflow-wrap: anywhere;
}
.delta-collapse-title-row {
  display: flex;
  align-items: center;
}
.delta-review-collapse {
  background: transparent;
}
.delta-tools-empty {
  font-size: 12px;
  color: v-bind('themeVars.textColor3');
  padding: 6px 0;
}
.delta-tools-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0;
}
.delta-tool-item {
  background: rgba(128, 128, 128, 0.04);
  border: 1px solid rgba(128, 128, 128, 0.1);
  border-radius: 6px;
  padding: 8px 10px;
}
.delta-tool-item.delta-tool-status-success {
  border-left: 3px solid #16a34a;
}
.delta-tool-item.delta-tool-status-warning {
  border-left: 3px solid #d97706;
}
.delta-tool-item.delta-tool-status-error {
  border-left: 3px solid #dc2626;
}
.delta-tool-item.delta-tool-status-pending {
  border-left: 3px solid #2563eb;
}
.delta-tool-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.delta-tool-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.delta-tool-body {
  font-size: 12px;
  line-height: 1.5;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.delta-tool-detail {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.delta-tool-label {
  color: v-bind('themeVars.textColor3');
  flex-shrink: 0;
}
.delta-changes-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 0;
}
.delta-change-card {
  background: rgba(128, 128, 128, 0.03);
  border: 1px solid rgba(128, 128, 128, 0.1);
  border-radius: 6px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.delta-change-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.delta-change-card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}
.delta-para-badge {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
  color: v-bind('themeVars.primaryColor');
  background: rgba(128, 128, 128, 0.08);
  padding: 1px 6px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}
.delta-para-badge:hover {
  background: v-bind('themeVars.primaryColor');
  color: #fff;
}
.delta-jump-btn {
  font-size: 12px;
  opacity: 0.8;
}
.delta-jump-btn:hover {
  opacity: 1;
}
.delta-change-diff-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-radius: 4px;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.6;
}
.delta-diff-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 4px;
}
.delta-diff-orig {
  background: rgba(239, 68, 68, 0.05);
  border-left: 3px solid rgba(239, 68, 68, 0.4);
}
.delta-diff-new {
  background: rgba(34, 197, 94, 0.06);
  border-left: 3px solid rgba(34, 197, 94, 0.6);
}
.delta-diff-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 0 4px;
  border-radius: 3px;
  flex-shrink: 0;
  user-select: none;
}
.delta-diff-orig .delta-diff-badge {
  color: #dc2626;
  background: rgba(239, 68, 68, 0.12);
}
.delta-diff-new .delta-diff-badge {
  color: #16a34a;
  background: rgba(34, 197, 94, 0.14);
}
.delta-diff-content {
  flex: 1;
  word-break: break-all;
  color: v-bind('themeVars.textColor1');
}
.delta-diff-del {
  background-color: rgba(239, 68, 68, 0.18);
  color: #dc2626;
  text-decoration: line-through;
  padding: 1px 3px;
  border-radius: 3px;
  font-weight: 500;
}
.delta-diff-ins {
  background-color: rgba(34, 197, 94, 0.2);
  color: #15803d;
  text-decoration: none;
  padding: 1px 3px;
  border-radius: 3px;
  font-weight: 500;
}
.delta-change-reason {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: v-bind('themeVars.textColor3');
  background: rgba(128, 128, 128, 0.04);
  padding: 5px 8px;
  border-radius: 4px;
}
.delta-reason-prefix {
  font-weight: 500;
  flex-shrink: 0;
}
.delta-reason-text {
  flex: 1;
  overflow-wrap: anywhere;
}
.delta-row-highlighted {
  animation: deltaRowPulse 2.4s ease-out;
}
@keyframes deltaRowPulse {
  0% {
    background-color: rgba(24, 160, 88, 0.35);
  }
  30% {
    background-color: rgba(24, 160, 88, 0.35);
  }
  100% {
    background-color: transparent;
  }
}
</style>
