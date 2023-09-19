import { api } from './api';
import { Page } from './page';
import { runCatching } from './result';

// Upload
export interface WenkuUploadHistory {
  id: string;
  novelId: string;
  volumeId: string;
  uploader: string;
  createAt: number;
}

const listUploadHistory = (page: number) =>
  runCatching(
    api
      .get('wenku-novel-admin/upload/', { searchParams: { page } })
      .json<Page<WenkuUploadHistory>>()
  );

const deleteUploadHistory = (id: string) =>
  runCatching(api.delete(`wenku-novel-admin/upload/${id}`).text());

export const ApiWenkuNovelHistory = {
  listUploadHistory,
  deleteUploadHistory,
};
