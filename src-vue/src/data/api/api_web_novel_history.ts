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
  return runCatching(
    api.get(`toc-merge/list`, { searchParams: { page } }).json()
  );
}

export type TocMergeHistoryDto = TocMergeHistoryOutlineDto & {
  tocOld: WebNovelTocItemDto[];
  tocNew: WebNovelTocItemDto[];
};

async function getTocMergeHistory(
  id: string
): Promise<Result<TocMergeHistoryDto>> {
  return runCatching(api.get(`toc-merge/item/${id}`).json());
}

async function deleteMergeHistory(
  id: string,
  token: string
): Promise<Result<string>> {
  return runCatching(
    api
      .delete(`toc-merge/item/${id}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .text()
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
  return runCatching(api.get(`patch/list`, { searchParams: { page } }).json());
}

async function getPatch(
  providerId: string,
  novelId: string
): Promise<Result<WebNovelPatchHistoryDto>> {
  return runCatching(api.get(`patch/${providerId}/${novelId}`).json());
}

async function deletePatch(
  providerId: string,
  novelId: string,
  token: string
): Promise<Result<string>> {
  return runCatching(
    api
      .delete(`patch/${providerId}/${novelId}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .text()
  );
}

async function revokePatch(
  providerId: string,
  novelId: string,
  token: string
): Promise<Result<string>> {
  return runCatching(
    api
      .post(`patch/${providerId}/${novelId}/revoke`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .text()
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
