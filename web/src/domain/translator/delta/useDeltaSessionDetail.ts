import { onScopeDispose, ref, shallowRef, watch } from 'vue';
import type { DeltaChapterSession } from '@auto-novel/translator';
import type { DeltaSessionSummary } from './session-summary';

export type DeltaSessionLoader = (
  id: string,
) => Promise<DeltaChapterSession | undefined>;
interface DetailOptions {
  summary: () => DeltaSessionSummary | null | undefined;
  enabled: () => boolean;
  load: () => DeltaSessionLoader | undefined;
}
interface DetailRead {
  id: string;
  runId: string;
  revision: number;
  epoch: number;
  load: DeltaSessionLoader;
  force: boolean;
}

/** Hold one visible checkpoint, serializing reads and coalescing newer revisions. */
export function useDeltaSessionDetail(options: DetailOptions) {
  const session = shallowRef<DeltaChapterSession | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const retryVersion = ref(0);
  let epoch = 0;
  let pending = false;
  let disposed = false;
  let wanted: DetailRead | null = null;

  async function readLatest() {
    if (disposed || pending || !wanted) return;
    const request = wanted;
    if (
      !request.force &&
      session.value &&
      session.value.revision >= request.revision
    ) {
      loading.value = false;
      return;
    }
    pending = true;
    loading.value = true;
    try {
      const snapshot = await request.load(request.id);
      if (disposed || request.epoch !== epoch || !wanted) return;
      if (!snapshot) throw new Error('Delta 检查点不存在，请重试');
      if (
        snapshot.id !== request.id ||
        snapshot.runId !== request.runId ||
        snapshot.revision < request.revision
      )
        throw new Error('Delta 检查点版本已变化，请重试');
      if (snapshot.revision >= wanted.revision) {
        session.value = snapshot;
        error.value = null;
      }
    } catch (cause) {
      if (!disposed && wanted === request)
        error.value = cause instanceof Error ? cause.message : String(cause);
    } finally {
      pending = false;
      if (!disposed && wanted !== request && wanted) void readLatest();
      else if (!disposed) loading.value = false;
    }
  }

  const stop = watch(
    [
      options.enabled,
      () => options.summary()?.id,
      () => options.summary()?.runId,
      () => options.summary()?.revision,
      options.load,
      () => retryVersion.value,
    ],
    ([enabled, id, runId, revision, load, retry], previous) => {
      const sameChapter =
        enabled && id === wanted?.id && runId === wanted?.runId;
      if (!sameChapter) {
        epoch++;
        session.value = null;
      }
      error.value = null;
      if (
        !enabled ||
        !id ||
        runId === undefined ||
        revision === undefined ||
        !load
      ) {
        wanted = null;
        loading.value = false;
        if (enabled && id && !load) error.value = '未配置 Delta 详情读取器';
        return;
      }
      wanted = {
        id,
        runId,
        revision,
        load,
        epoch,
        force: retry !== previous?.[5],
      };
      loading.value = pending;
      void readLatest();
    },
    { immediate: true, flush: 'sync' },
  );
  onScopeDispose(() => {
    stop();
    disposed = true;
    wanted = null;
    epoch++;
    session.value = null;
    loading.value = false;
    error.value = null;
  });
  return { session, loading, error, retry: () => retryVersion.value++ };
}
