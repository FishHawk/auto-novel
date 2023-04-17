import api from './api';
import { Result, runCatching } from './result';

export interface BookPatchPageItemDto {
  providerId: string;
  bookId: string;
  titleJp: string;
  titleZh?: string;
}

export interface BookPatchPageDto {
  total: number;
  items: BookPatchPageItemDto[];
}

export interface BookPatchDto {
  providerId: string;
  bookId: string;
  titleJp: string;
  titleZh?: string;
  patches: BookMetadataPatchDto[];
  toc: { [key: string]: BookEpisodePatchesDto };
}

interface BookMetadataTextChange {
  jp: string;
  zhOld?: string;
  zhNew: string;
}

interface BookMetadataPatchDto {
  uuid: String;
  titleChange?: BookMetadataTextChange;
  introductionChange?: BookMetadataTextChange;
  glossary?: { [key: string]: string };
  tocChange: BookMetadataTextChange[];
  createAt: number;
}

interface BookEpisodePatchesDto {
  titleJp: string;
  titleZh?: string;
  patches: BookEpisodePatchDto[];
}

interface BookEpisodeTextChange {
  jp: string;
  zhOld: string;
  zhNew: string;
}

interface BookEpisodePatchDto {
  uuid: string;
  paragraphsChange: BookEpisodeTextChange[];
  createAt: number;
}

async function list(page: number): Promise<Result<BookPatchPageDto>> {
  return runCatching(api.get(`patch/list`, { searchParams: { page } }).json());
}

async function getPatch(
  providerId: string,
  bookId: string
): Promise<Result<BookPatchDto>> {
  return runCatching(api.get(`patch/self/${providerId}/${bookId}`).json());
}

async function deletePatch(
  providerId: string,
  bookId: string,
  token: string
): Promise<Result<string>> {
  return runCatching(
    api
      .delete(`patch/self/${providerId}/${bookId}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .text()
  );
}

export default {
  list,
  getPatch,
  deletePatch,
};
