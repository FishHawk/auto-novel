import api from './api';
import { Result, runCatching } from './result';

export interface BookPatchesPageItemDto {
  providerId: string;
  bookId: string;
  titleJp: string;
  titleZh?: string;
}

export interface BookPatchesPageDto {
  total: number;
  items: BookPatchesPageItemDto[];
}

export interface BookPatchesDto {
  providerId: string;
  bookId: string;
  titleJp: string;
  titleZh?: string;
  patches: BookPatchDto[];
}

interface TextChange {
  jp: string;
  zhOld?: string;
  zhNew: string;
}

interface BookPatchDto {
  uuid: String;
  titleChange?: TextChange;
  introductionChange?: TextChange;
  glossary?: { [key: string]: string };
  tocChange: TextChange[];
  createAt: number;
}

async function listPatch(page: number): Promise<Result<BookPatchesPageDto>> {
  return runCatching(api.get(`patch/list`, { searchParams: { page } }).json());
}

async function getPatch(
  providerId: string,
  bookId: string
): Promise<Result<BookPatchesDto>> {
  return runCatching(api.get(`patch/${providerId}/${bookId}`).json());
}

async function deletePatch(
  providerId: string,
  bookId: string,
  token: string
): Promise<Result<string>> {
  return runCatching(
    api
      .delete(`patch/${providerId}/${bookId}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .text()
  );
}

async function revokePatch(
  providerId: string,
  bookId: string,
  token: string
): Promise<Result<string>> {
  return runCatching(
    api
      .post(`patch/${providerId}/${bookId}/revoke`, {
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
