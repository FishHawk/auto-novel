import api from './api';
import { WebNovelTocItemDto } from './api_web_novel';
import { Result, runCatching } from './result';

export interface TocMergeHistoryPageDto {
  total: number;
  items: TocMergeHistoryPageItemDto[];
}
interface TocMergeHistoryPageItemDto {
  id: string;
  providerId: string;
  novelId: string;
  reason: string;
}

async function listTocMergeHistory(
  page: number
): Promise<Result<TocMergeHistoryPageDto>> {
  return runCatching(
    api.get(`toc-merge/list`, { searchParams: { page } }).json()
  );
}


export type TocMergeHistoryDto = TocMergeHistoryPageItemDto & {
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

export default {
  listTocMergeHistory,
  getTocMergeHistory,
  deleteMergeHistory,
};
