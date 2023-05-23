import api from './api';
import { Result, runCatching } from './result';

export interface WebNovelPatchHistoryPageItemDto {
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
}

export interface WebNovelPatchHistoryPageDto {
  total: number;
  items: WebNovelPatchHistoryPageItemDto[];
}

export interface WebNovelPatchHistoryDto {
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
  patches: WebNovelPatchDto[];
}

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
): Promise<Result<WebNovelPatchHistoryPageDto>> {
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

export default {
  listPatch,
  getPatch,
  deletePatch,
  revokePatch,
};
