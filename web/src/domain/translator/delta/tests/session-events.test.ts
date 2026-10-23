import { describe, expect, it } from 'vitest';
import { applyDeltaSessionEvents, groupDeltaSessions } from '../session-events';
import { summarizeDeltaSession } from '../session-summary';
import { createDeltaSession } from '@auto-novel/translator';

function summary(chapterId: string) {
  return summarizeDeltaSession(
    createDeltaSession({
      runId: chapterId,
      workerId: 'worker',
      identity: { taskId: 'task', novelId: 'novel', chapterId },
      source: ['原文'],
      glossaryId: 'g',
      glossary: {},
      model: {
        model: 'model',
        endpoint: 'https://model.test',
        protocol: 'openai-completions',
      },
    }),
  );
}

describe('Delta session list notifications', () => {
  it('does not invalidate another task card when a different task advances', () => {
    const a = summary('a');
    const b = {
      ...summary('b'),
      identity: { ...summary('b').identity, taskId: 'other-task' },
    };
    const previous = groupDeltaSessions([a, b]);
    const next = groupDeltaSessions(
      [{ ...a, revision: a.revision + 1 }, b],
      previous,
    );
    expect(next.get('other-task')).toBe(previous.get('other-task'));
    expect(next.get('task')).not.toBe(previous.get('task'));
  });
  it('replaces just one chapter and preserves other summary identities', () => {
    const a = summary('a');
    const b = summary('b');
    const original = [a, b];
    const updated = { ...a, revision: a.revision + 1 };
    const result = applyDeltaSessionEvents(original, [
      { type: 'upsert', session: updated },
    ]);
    expect(result).toEqual([updated, b]);
    expect(result[1]).toBe(b);
    expect(original[0]).toBe(a);
    expect(
      applyDeltaSessionEvents(result, [
        { type: 'upsert', session: summary('c') },
      ]),
    ).toHaveLength(3);
  });
  it('resets from repository state and removes only specified chapters', () => {
    const a = summary('a');
    const b = summary('b');
    const result = applyDeltaSessionEvents(
      [a, b],
      [{ type: 'remove', ids: [a.id] }],
    );
    expect(result).toEqual([b]);
    expect(result[0]).toBe(b);
    expect(
      applyDeltaSessionEvents(result, [{ type: 'reset', sessions: [] }]),
    ).toEqual([]);
  });
  it('applies a notification burst with one list rewrite and stable order', () => {
    const a = summary('a');
    const b = summary('b');
    const c = summary('c');
    const updatedA = { ...a, revision: a.revision + 1 };
    const result = applyDeltaSessionEvents(
      [a, b],
      [
        { type: 'upsert', session: updatedA },
        { type: 'upsert', session: c },
        { type: 'remove', ids: [b.id] },
        { type: 'upsert', session: { ...c, revision: c.revision + 1 } },
      ],
    );
    expect(result.map((item) => item.id)).toEqual([a.id, c.id]);
    expect(result[0]).toBe(updatedA);
    expect(result[1].revision).toBe(c.revision + 1);
  });
  it('lets a reset replace the list before later upserts in the same burst', () => {
    const a = summary('a');
    const b = summary('b');
    const reset = { ...summary('c'), revision: 9 };
    const result = applyDeltaSessionEvents(
      [a],
      [
        { type: 'reset', sessions: [reset] },
        { type: 'upsert', session: b },
      ],
    );
    expect(result.map((item) => item.id)).toEqual([reset.id, b.id]);
    expect(result[0]).toBe(reset);
  });
});
