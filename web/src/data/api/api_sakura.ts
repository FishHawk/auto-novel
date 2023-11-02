import { runCatching } from '@/data/result';

import { client } from './client';

export interface SakuraStatus {
  jobs: SakuraJob[];
  workers: SakuraWorker[];
}

interface SakuraJob {
  id: string;
  task: string;
  description: string;
  workerId?: string;
  submitter: string;
  createAt: number;
}

interface SakuraWorker {
  id: string;
  active: boolean;
  endpoint: string;
  gpu: string;
  description: string;
  progress: {
    total: number;
    finished: number;
  } | null;
}

const getSakuraStatus = () =>
  runCatching(client.get('sakura').json<SakuraStatus>());

const createSakuraJob = (task: string) =>
  runCatching(client.post(`sakura/job`, { body: task }).text());

const createSakuraJobWebTranslate = (
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
  return createSakuraJob(`web/${providerId}/${novelId}${queryString}`);
};

const deleteSakuraJob = (id: string) =>
  runCatching(client.delete(`sakura/job/${id}`).text());

const createSakuraWorker = (json: { gpu: string; endpoint: string }) =>
  runCatching(client.post('sakura/worker', { json }).text());

const deleteSakuraWorker = (id: string) =>
  runCatching(client.delete(`sakura/worker/${id}`).text());

const startSakuraWorker = (id: string) =>
  runCatching(client.post(`sakura/worker/${id}/start`).text());

const stopSakuraWorker = (id: string) =>
  runCatching(client.post(`sakura/worker/${id}/stop`).text());

export const ApiSakura = {
  getSakuraStatus,
  //
  createSakuraJobWebTranslate,
  deleteSakuraJob,
  //
  createSakuraWorker,
  deleteSakuraWorker,
  startSakuraWorker,
  stopSakuraWorker,
};
