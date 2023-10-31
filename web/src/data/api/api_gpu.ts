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
  gpu: string;
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

const createGpuJobWebTranslate = (
  providerId: string,
  novelId: string,
  params: {
    start: number;
    end: number;
  }
) => {
  const paramsString: { [key: string]: string } = {};
  if (params.start > 0) paramsString['start'] = params.start.toString();
  if (params.end < 65535) paramsString['end'] = params.end.toString();
  const searchParams = new URLSearchParams(paramsString).toString();
  const queryString = searchParams ? `?${searchParams}` : '';
  return createGpuJob(`web/${providerId}/${novelId}${queryString}`);
};

const deleteGpuJob = (id: string) =>
  runCatching(client.delete(`gpu/job/${id}`).text());

const createGpuWorker = (json: { gpu: string; endpoint: string }) =>
  runCatching(client.post('gpu/worker', { json }).text());

const deleteGpuWorker = (id: string) =>
  runCatching(client.delete(`gpu/worker/${id}`).text());

const startGpuWorker = (id: string) =>
  runCatching(client.post(`gpu/worker/${id}/start`).text());

const stopGpuWorker = (id: string) =>
  runCatching(client.post(`gpu/worker/${id}/stop`).text());

export const ApiGpu = {
  getGpuInfo,
  //
  createGpuJobWebTranslate,
  deleteGpuJob,
  //
  createGpuWorker,
  deleteGpuWorker,
  startGpuWorker,
  stopGpuWorker,
};
