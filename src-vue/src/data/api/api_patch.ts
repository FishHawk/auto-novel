import api from './api';
import { Ok, Err, Result } from './result';

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

interface BookMetadataPatchBody {
  title?: string;
  introduction?: string;
  toc: { [key: string]: string };
}

interface BookEpisodePatchBody {
  paragraphs: { [key: number]: string };
}

async function list(page: number): Promise<Result<BookPatchPageDto>> {
  return api
    .get(`patch/list`, { searchParams: { page } })
    .json<BookPatchPageDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function getPatch(
  providerId: string,
  bookId: string
): Promise<Result<BookPatchDto>> {
  return api
    .get(`patch/self/${providerId}/${bookId}`)
    .json<BookPatchDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function deletePatch(
  providerId: string,
  bookId: string
): Promise<Result<string>> {
  return api
    .delete(`patch/self/${providerId}/${bookId}`)
    .text()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function postMetadataPatch(
  providerId: string,
  bookId: string,
  patch: BookMetadataPatchBody
): Promise<Result<string>> {
  return api
    .post(`patch/metadata/${providerId}/${bookId}`, {
      json: patch,
    })
    .text()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function postEpisodePatch(
  providerId: string,
  bookId: string,
  episodeId: string,
  patch: BookEpisodePatchBody
): Promise<Result<string>> {
  return api
    .post(`patch/episode/${providerId}/${bookId}/${episodeId}`, {
      json: patch,
    })
    .text()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export default {
  list,
  getPatch,
  // deletePatch,
  postMetadataPatch,
  postEpisodePatch,
};
