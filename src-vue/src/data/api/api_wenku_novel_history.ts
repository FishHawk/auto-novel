import api from './api';
import { Page } from './page';
import { Result, runCatching } from './result';

// Upload
export interface WenkuUploadHistory {
  id: string;
  novelId: string;
  volumeId: string;
  uploader: string;
  createAt: number;
}

async function listUploadHistory(
  page: number
): Promise<Result<Page<WenkuUploadHistory>>> {
  const url = `wenku-novel-admin/upload/`;
  return runCatching(api.get(url, { searchParams: { page } }).json());
}

async function deleteUploadHistory(
  id: string,
  token: string
): Promise<Result<string>> {
  const url = `wenku-novel-admin/upload/${id}`;
  return runCatching(
    api.delete(url, { headers: { Authorization: 'Bearer ' + token } }).text()
  );
}
export const ApiWenkuNovelHistory = {
  listUploadHistory,
  deleteUploadHistory,
};
