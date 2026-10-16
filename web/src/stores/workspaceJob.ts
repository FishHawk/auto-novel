import type { TranslateJob } from '@/model/Translator';

export const addWorkspaceJob = (jobs: TranslateJob[], job: TranslateJob) => {
  const conflictJobIndex = jobs.findIndex((it) => it.task === job.task);
  if (conflictJobIndex === -1) {
    jobs.push(job);
    return true;
  }

  const conflictJob = jobs[conflictJobIndex];
  if (conflictJob.finishAt === undefined) {
    return false;
  }

  jobs[conflictJobIndex] = {
    task: job.task,
    description: job.description,
    createAt: job.createAt,
  };
  return true;
};

/** 一次排队可能拆成多个任务，需要按实际结果提示，不能只看第一个。 */
export const summarizeQueueResults = (results: boolean[]) => {
  if (results.length === 0) {
    return { type: 'error', text: '排队失败：没有选中章节' } as const;
  }
  const success = results.filter(Boolean).length;
  const failed = results.length - success;
  if (failed === 0) {
    return { type: 'success', text: '排队成功' } as const;
  } else if (success === 0) {
    return { type: 'error', text: '排队失败：翻译任务已经存在' } as const;
  } else {
    return {
      type: 'warning',
      text: `${success}个任务已排队，${failed}个任务已经存在`,
    } as const;
  }
};
