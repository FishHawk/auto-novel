import { describe, expect, it } from 'vitest';

import type { TranslateJob, TranslateJobRecord } from '../src/model/Translator';
import {
  addWorkspaceJob,
  summarizeQueueResults,
} from '../src/stores/workspaceJob';

describe('addWorkspaceJob', () => {
  it('adds a new job when no task conflicts', () => {
    const jobs: TranslateJob[] = [];
    const job: TranslateJob = {
      task: 'web/provider/novel?level=normal',
      description: 'Novel',
      createAt: 1,
    };

    expect(addWorkspaceJob(jobs, job)).toBe(true);
    expect(jobs).toEqual([job]);
  });

  it('rejects a duplicate unfinished job', () => {
    const jobs: TranslateJob[] = [
      {
        task: 'web/provider/novel?level=normal',
        description: 'Existing',
        createAt: 1,
      },
    ];

    const duplicate: TranslateJob = {
      task: 'web/provider/novel?level=normal',
      description: 'Duplicate',
      createAt: 2,
    };

    expect(addWorkspaceJob(jobs, duplicate)).toBe(false);
    expect(jobs).toEqual([
      {
        task: 'web/provider/novel?level=normal',
        description: 'Existing',
        createAt: 1,
      },
    ]);
  });

  it('requeues a duplicate finished job as a fresh job', () => {
    const jobs: TranslateJobRecord[] = [
      {
        task: 'web/provider/novel?level=normal',
        description: 'Existing',
        createAt: 1,
        finishAt: 10,
        progress: { finished: 3, error: 0, total: 3 },
      },
    ];

    const duplicate: TranslateJob = {
      task: 'web/provider/novel?level=normal',
      description: 'Updated',
      createAt: 2,
    };

    expect(addWorkspaceJob(jobs, duplicate)).toBe(true);
    expect(jobs).toEqual([
      {
        task: 'web/provider/novel?level=normal',
        description: 'Updated',
        createAt: 2,
      },
    ]);
  });
});

describe('summarizeQueueResults', () => {
  it('reports success only when at least one task was selected and every split task was queued', () => {
    expect(summarizeQueueResults([])).toEqual({
      type: 'error',
      text: '排队失败：没有选中章节',
    });
    expect(summarizeQueueResults([true, true])).toEqual({
      type: 'success',
      text: '排队成功',
    });
  });

  it('reports failure when every split task already exists', () => {
    expect(summarizeQueueResults([false, false]).type).toBe('error');
  });

  it('reports partial results instead of a plain success', () => {
    expect(summarizeQueueResults([true, false, false])).toEqual({
      type: 'warning',
      text: '1个任务已排队，2个任务已经存在',
    });
  });
});
