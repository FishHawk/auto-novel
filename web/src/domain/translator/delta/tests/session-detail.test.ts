import { afterEach, describe, expect, it, vi } from 'vitest';
import { effectScope, nextTick, ref } from 'vue';
import type { DeltaChapterSession } from '@auto-novel/translator';
import type { DeltaSessionSummary } from '../session-summary';
import { useDeltaSessionDetail } from '../useDeltaSessionDetail';

const cleanups: (() => void)[] = [];
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));
const summary = (id = 'a', revision = 1) =>
  ({ id, runId: 'run', revision }) as DeltaSessionSummary;
const detail = (id = 'a', revision = 1) =>
  ({
    id,
    runId: 'run',
    revision,
    source: ['large source'],
  }) as DeltaChapterSession;
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => (resolve = done));
  return { promise, resolve };
}
async function flush() {
  await Promise.resolve();
  await nextTick();
}
function fixture(
  load: (id: string) => Promise<DeltaChapterSession | undefined>,
) {
  const selected = ref<DeltaSessionSummary | null>(summary());
  const enabled = ref(false);
  const scope = effectScope();
  const state = scope.run(() =>
    useDeltaSessionDetail({
      summary: () => selected.value,
      enabled: () => enabled.value,
      load: () => load,
    }),
  )!;
  cleanups.push(() => scope.stop());
  return { selected, enabled, state, scope };
}

describe('visible Delta session details', () => {
  it('loads only while visible and drops the full snapshot when closed', async () => {
    const load = vi.fn(async () => detail());
    const { enabled, state } = fixture(load);
    await flush();
    expect(load).not.toHaveBeenCalled();
    enabled.value = true;
    await flush();
    expect(load).toHaveBeenCalledOnce();
    expect(state.session.value?.source).toEqual(['large source']);
    enabled.value = false;
    expect(state.session.value).toBeNull();
    expect(state.loading.value).toBe(false);
  });

  it('ignores an in-flight snapshot after close or scope disposal', async () => {
    const pending = deferred<DeltaChapterSession>();
    const { enabled, state, scope } = fixture(() => pending.promise);
    enabled.value = true;
    enabled.value = false;
    pending.resolve(detail());
    await flush();
    expect(state.session.value).toBeNull();
    enabled.value = true;
    await flush();
    expect(state.session.value).not.toBeNull();
    scope.stop();
    expect(state.session.value).toBeNull();
  });

  it('never lets an older selection replace the current chapter', async () => {
    const first = deferred<DeltaChapterSession>();
    const second = deferred<DeltaChapterSession>();
    const { selected, enabled, state } = fixture((id) =>
      id === 'a' ? first.promise : second.promise,
    );
    enabled.value = true;
    selected.value = summary('b');
    second.resolve(detail('b'));
    await flush();
    first.resolve(detail('a'));
    await flush();
    expect(state.session.value?.id).toBe('b');
  });

  it('fetches the latest revision even when it advances during a read', async () => {
    const first = deferred<DeltaChapterSession>();
    const second = deferred<DeltaChapterSession>();
    const load = vi
      .fn()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const { selected, enabled, state } = fixture(load);
    enabled.value = true;
    selected.value = summary('a', 2);
    first.resolve(detail('a', 1));
    await flush();
    expect(state.session.value).toBeNull();
    second.resolve(detail('a', 2));
    await flush();
    expect(load).toHaveBeenCalledTimes(2);
    expect(state.session.value?.revision).toBe(2);
    selected.value = { ...selected.value! };
    await flush();
    expect(load).toHaveBeenCalledTimes(2);
  });

  it('shows a retryable error for a missing or mismatched snapshot', async () => {
    const load = vi
      .fn()
      .mockResolvedValueOnce(detail('a', 0))
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce(detail());
    const { enabled, state } = fixture(load);
    enabled.value = true;
    await flush();
    expect(state.session.value).toBeNull();
    expect(state.error.value).toBeTruthy();
    state.retry();
    await flush();
    expect(state.error.value).toBeTruthy();
    state.retry();
    await flush();
    expect(state.session.value?.revision).toBe(1);
    expect(state.error.value).toBeNull();
  });

  it('retains the same chapter snapshot while refreshing and releases it on unmount', async () => {
    const load = vi.fn(async () => detail('a', load.mock.calls.length));
    const { selected, enabled, state, scope } = fixture(load);
    enabled.value = true;
    await flush();
    selected.value = summary('a', 2);
    expect(state.session.value?.revision).toBe(1);
    expect(state.loading.value).toBe(true);
    await flush();
    expect(state.session.value?.revision).toBe(2);
    scope.stop();
    expect(state.session.value).toBeNull();
  });
  it('coalesces revision bursts into one follow-up read without overlapping history loads', async () => {
    const first = deferred<DeltaChapterSession>();
    const second = deferred<DeltaChapterSession>();
    const load = vi
      .fn()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const { selected, enabled, state } = fixture(load);
    enabled.value = true;
    selected.value = summary('a', 2);
    selected.value = summary('a', 3);
    selected.value = summary('a', 4);
    expect(load).toHaveBeenCalledTimes(1);
    first.resolve(detail('a', 1));
    await flush();
    expect(load).toHaveBeenCalledTimes(2);
    second.resolve(detail('a', 4));
    await flush();
    expect(state.session.value?.revision).toBe(4);
    expect(load).toHaveBeenCalledTimes(2);
  });

  it('accepts a persisted snapshot ahead of the summary without rereading it', async () => {
    const load = vi.fn(async () => detail('a', 2));
    const { selected, enabled, state } = fixture(load);
    enabled.value = true;
    await flush();
    expect(state.session.value?.revision).toBe(2);
    expect(state.error.value).toBeNull();
    selected.value = summary('a', 2);
    await flush();
    expect(load).toHaveBeenCalledTimes(1);
  });

  it('does not repopulate or enqueue a queued revision after unmount', async () => {
    const pending = deferred<DeltaChapterSession>();
    const load = vi.fn(() => pending.promise);
    const { selected, enabled, state, scope } = fixture(load);
    enabled.value = true;
    selected.value = summary('a', 2);
    scope.stop();
    pending.resolve(detail('a', 2));
    await flush();
    expect(state.session.value).toBeNull();
    expect(load).toHaveBeenCalledTimes(1);
  });
});
