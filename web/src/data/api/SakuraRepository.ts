import { client } from './client';

interface WebIncorrectCaseBody {
  providerId: string;
  novelId: string;
  chapterId: string;
  jp: string;
  zh: string;
  contextJp: string[];
  contextZh: string[];
}

const createWebIncorrectCase = (json: WebIncorrectCaseBody) =>
  client.post('sakura/incorrect-case', { json });

export const SakuraRepository = {
  createWebIncorrectCase,
};
