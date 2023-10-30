import { runCatching } from '@/data/result';

import { client } from './client';

export interface GpuInfo {
  jobs: GpuJob[];
  workers: GpuWorker[];
}

interface GpuJob {
  id: string;
  task: string;
  description: string;
  workerUuid?: string;
  submitter: string;
  createAt: number;
}

interface GpuWorker {
  id: string;
  active: boolean;
  card: string;
  description: string;
  progress: {
    total: number;
    finished: number;
    error: number;
  } | null;
}

const getGpuInfo = () => runCatching(client.get('gpu').json<GpuInfo>());

const createGpuJob = (task: string) =>
  runCatching(client.post(`gpu/job`, { body: task }).text());

const createGpuJobWebTranslate = (providerId: string, novelId: string) =>
  createGpuJob(`web/${providerId}/${novelId}`);

const deleteGpuJob = (id: string) =>
  runCatching(client.delete(`gpu/job/${id}`).text());

export const ApiGpu = {
  getGpuInfo,
  createGpuJobWebTranslate,
  deleteGpuJob,
};
