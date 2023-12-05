import { runCatching } from '@/data/result';

import { client } from './client';

export interface SakuraStatus {
  jobs: SakuraJob[];
  workers: SakuraWorker[];
}

export interface SakuraJob {
  id: string;
  task: string;
  description: string;
  workerId?: string;
  submitter: string;
  createAt: number;
}

export interface SakuraWorker {
  id: string;
  username: string;
  active: boolean;
  endpoint?: string;
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

const buildSakuraTaskQueryString = ({
  start,
  end,
  expire,
}: {
  start: number;
  end: number;
  expire: boolean;
}) => {
  const searchParamsInit: { [key: string]: string } = {};
  if (start > 0) searchParamsInit['start'] = start.toString();
  if (end < 65535) searchParamsInit['end'] = end.toString();
  if (expire) searchParamsInit['expire'] = expire.toString();
  const searchParams = new URLSearchParams(searchParamsInit).toString();
  return searchParams ? `?${searchParams}` : '';
};

const createSakuraJobWebTranslate = (
  providerId: string,
  novelId: string,
  params: {
    start: number;
    end: number;
    expire: boolean;
  }
) =>
  createSakuraJob(
    `web/${providerId}/${novelId}` + buildSakuraTaskQueryString(params)
  );

const createSakuraJobWenkuTranslate = (
  novelId: string,
  volumeId: string,
  params: {
    start: number;
    end: number;
    expire: boolean;
  }
) =>
  createSakuraJob(
    `wenku/${novelId}/${volumeId}` + buildSakuraTaskQueryString(params)
  );

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

const createWebIncorrectCase = (json: {
  providerId: string;
  novelId: string;
  chapterId: string;
  jp: string;
  zh: string;
  contextJp: string[];
  contextZh: string[];
}) => runCatching(client.post('sakura/incorrect-case', { json }).text());

export const ApiSakura = {
  getSakuraStatus,
  //
  createSakuraJobWebTranslate,
  createSakuraJobWenkuTranslate,
  deleteSakuraJob,
  //
  buildSakuraTaskQueryString,
  createSakuraWorker,
  deleteSakuraWorker,
  startSakuraWorker,
  stopSakuraWorker,
  //
  createWebIncorrectCase,
};
