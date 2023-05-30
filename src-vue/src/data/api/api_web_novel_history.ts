import api from './api';
import { WebNovelTocItemDto } from './api_web_novel';
import { Page } from './page';
import { Result, runCatching } from './result';

// Toc merge
export interface TocMergeHistoryOutlineDto {
  id: string;
  providerId: string;
  novelId: string;
  reason: string;
}

async function listTocMergeHistory(
  page: number
): Promise<Result<Page<TocMergeHistoryOutlineDto>>> {
  const url = `web-novel-admin/toc-merge/`;
  return runCatching(api.get(url, { searchParams: { page } }).json());
}

export type TocMergeHistoryDto = TocMergeHistoryOutlineDto & {
  tocOld: WebNovelTocItemDto[];
  tocNew: WebNovelTocItemDto[];
};

async function getTocMergeHistory(
  id: string
): Promise<Result<TocMergeHistoryDto>> {
  const url = `web-novel-admin/toc-merge/${id}`;
  return runCatching(api.get(url).json());
}

async function deleteMergeHistory(
  id: string,
  token: string
): Promise<Result<string>> {
  const url = `web-novel-admin/toc-merge/${id}`;
  return runCatching(
    api.delete(url, { headers: { Authorization: 'Bearer ' + token } }).text()
  );
}

// Patch
export interface WebNovelPatchHistoryOutlineDto {
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
}

export type WebNovelPatchHistoryDto = WebNovelPatchHistoryOutlineDto & {
  patches: WebNovelPatchDto[];
};

interface TextChange {
  jp: string;
  zhOld?: string;
  zhNew: string;
}

interface WebNovelPatchDto {
  uuid: String;
  titleChange?: TextChange;
  introductionChange?: TextChange;
  glossary?: { [key: string]: string };
  tocChange: TextChange[];
  createAt: number;
}

async function listPatch(
  page: number
): Promise<Result<Page<WebNovelPatchHistoryOutlineDto>>> {
  const url = `web-novel-admin/patch/`;
  return runCatching(api.get(url, { searchParams: { page } }).json());
}

async function getPatch(
  providerId: string,
  novelId: string
): Promise<Result<WebNovelPatchHistoryDto>> {
  const url = `web-novel-admin/patch/${providerId}/${novelId}`;
  return runCatching(api.get(url).json());
}

async function deletePatch(
  providerId: string,
  novelId: string,
  token: string
): Promise<Result<string>> {
  const url = `web-novel-admin/patch/${providerId}/${novelId}`;
  return runCatching(
    api.delete(url, { headers: { Authorization: 'Bearer ' + token } }).text()
  );
}

async function revokePatch(
  providerId: string,
  novelId: string,
  token: string
): Promise<Result<string>> {
  const url = `web-novel-admin/patch/${providerId}/${novelId}/revoke`;
  return runCatching(
    api.post(url, { headers: { Authorization: 'Bearer ' + token } }).text()
  );
}

export const ApiWebNovelHistory = {
  listTocMergeHistory,
  getTocMergeHistory,
  deleteMergeHistory,
  //
  listPatch,
  getPatch,
  deletePatch,
  revokePatch,
};
