import { describe, expect, it } from 'vitest';
import { TaskState } from '../../TaskState';

function chapterState(task: TaskState, chapterId: string) {
  const state = task.getChapterState(chapterId)!;
  state.onSegmentsReady(['原文'], [{ start: 0, end: 1 }]);
  state.onSegComplete(0, ['译文']);
  return state;
}

describe('chapter text retention', () => {
  it('releases older finished chapter text while keeping progress counts', () => {
    const task = new TaskState('task');
    task.initChapters(
      Array.from({ length: 10 }, (_, index) => ({
        chapterId: String(index),
        title: String(index),
        order: index,
        status: 'done' as const,
      })),
    );
    const states = Array.from({ length: 10 }, (_, index) =>
      chapterState(task, String(index)),
    );

    task.releaseFinishedChapterTexts(3);

    expect(states.slice(0, 7).every((state) => state.textReleased)).toBe(true);
    expect(
      states
        .slice(0, 7)
        .every((state) =>
          state.segments.every(
            (segment) =>
              segment.lines.length === 0 &&
              segment.translatedLines.length === 0,
          ),
        ),
    ).toBe(true);
    expect(states.slice(7).every((state) => !state.textReleased)).toBe(true);
    expect(states[0].completedCount).toBe(1);
    expect(states[0].segments).toHaveLength(1);
  });

  it('never releases a chapter whose segment is still translating', () => {
    const task = new TaskState('task');
    task.initChapters([
      { chapterId: 'a', title: 'A', order: 0, status: 'translating' },
    ]);
    const state = task.getChapterState('a')!;
    state.onSegmentsReady(['原文'], [{ start: 0, end: 1 }]);
    state.onSegStart(0, 'worker');

    expect(state.releaseText()).toBe(false);
    expect(state.textReleased).toBe(false);
    expect(state.segments[0].lines).toEqual(['原文']);
  });

  it('allows a released preview to be refilled after a fresh read', () => {
    const task = new TaskState('task');
    task.initChapters([
      { chapterId: 'a', title: 'A', order: 0, status: 'done' },
    ]);
    const state = chapterState(task, 'a');
    state.releaseText();
    expect(state.textReleased).toBe(true);
    state.injectDoneTranslation(['原文'], ['译文']);
    expect(state.textReleased).toBe(false);
    expect(state.segments[0].translatedLines).toEqual(['译文']);
  });
});
