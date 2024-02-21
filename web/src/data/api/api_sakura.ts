import { runCatching } from '@/data/result';

import { client } from './client';

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
  createWebIncorrectCase,
};
