import { api } from './api';
import { WebNovelTocItemDto } from './api_web_novel';
import { Page } from './page';
import { runCatching } from './result';

// Toc merge
export interface TocMergeHistoryOutlineDto {
  id: string;
  providerId: string;
  novelId: string;
  reason: string;
}

const listTocMergeHistory = (page: number) =>
  runCatching(
    api
      .get('web-novel-admin/toc-merge/', { searchParams: { page } })
      .json<Page<TocMergeHistoryOutlineDto>>()
  );

export type TocMergeHistoryDto = TocMergeHistoryOutlineDto & {
  tocOld: WebNovelTocItemDto[];
  tocNew: WebNovelTocItemDto[];
};

const getTocMergeHistory = (id: string) =>
  runCatching(
    api.get(`web-novel-admin/toc-merge/${id}`).json<TocMergeHistoryDto>()
  );

const deleteMergeHistory = (id: string) =>
  runCatching(api.delete(`web-novel-admin/toc-merge/${id}`).text());

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

const listPatch = (page: number) =>
  runCatching(
    api
      .get('web-novel-admin/patch/', { searchParams: { page } })
      .json<Page<WebNovelPatchHistoryOutlineDto>>()
  );

const getPatch = (providerId: string, novelId: string) =>
  runCatching(
    api
      .get(`web-novel-admin/patch/${providerId}/${novelId}`)
      .json<WebNovelPatchHistoryDto>()
  );

const deletePatch = (providerId: string, novelId: string) =>
  runCatching(
    api.delete(`web-novel-admin/patch/${providerId}/${novelId}`).text()
  );

const revokePatch = (providerId: string, novelId: string) =>
  runCatching(
    api.post(`web-novel-admin/patch/${providerId}/${novelId}/revoke`).text()
  );

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
