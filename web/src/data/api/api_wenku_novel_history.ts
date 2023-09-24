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
      .get('wenku-novel-admin/upload', { searchParams: { page } })
      .json<Page<WenkuUploadHistory>>()
  );

const deleteUploadHistory = (id: string) =>
  runCatching(api.delete(`wenku-novel-admin/upload/${id}`).text());

// Edit
interface WenkuEditHistoryData {
  title: string;
  titleZh: string;
  authors: string[];
  artists: string[];
  introduction: string;
}

export interface WenkuEditHistory {
  id: string;
  novelId: string;
  operator: string;
  old?: WenkuEditHistoryData;
  new: WenkuEditHistoryData;
  createAt: number;
}

const listEditHistory = (page: number) =>
  runCatching(
    api
      .get('wenku-novel-admin/edit', { searchParams: { page } })
      .json<Page<WenkuEditHistory>>()
  );

const deleteEditHistory = (id: string) =>
  runCatching(api.delete(`wenku-novel-admin/edit/${id}`).text());

export const ApiWenkuNovelHistory = {
  listUploadHistory,
  deleteUploadHistory,
  //
  listEditHistory,
  deleteEditHistory,
};
