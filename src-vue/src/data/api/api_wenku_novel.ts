import api from './api';
import { Result, runCatching } from './result';

export interface WenkuListPageDto {
  pageNumber: number;
  items: WenkuListItemDto[];
}
export interface WenkuListItemDto {
  bookId: string;
  title: string;
  cover: string;
  author: string;
  artists: string;
  keywords: string[];
}

async function list(
  page: number,
  query: string
): Promise<Result<WenkuListPageDto>> {
  return runCatching(
    api
      .get(`wenku/list`, {
        searchParams: { page, query },
      })
      .json()
  );
}

export interface WenkuMetadataDto {
  bookId: string;
  title: string;
  cover: string;
  author: string;
  artist: string;
  keywords: string[];
  introduction: string;
  updateAt: number;
  files: string[];
}

async function getMetadata(novelId: string): Promise<Result<WenkuMetadataDto>> {
  return runCatching(api.get(`wenku/metadata/${novelId}`).json());
}

interface MetadataCreateBody {
  bookId: string;
  title: string;
  cover: string;
  coverSmall: string;
  author: string;
  artist: string;
  keywords: string[];
  introduction: string;
}

async function postMetadata(
  body: MetadataCreateBody,
  token: string
): Promise<Result<String>> {
  return runCatching(
    api
      .post(`wenku/metadata/${body.bookId}`, {
        json: body,
        headers: { Authorization: 'Bearer ' + token },
      })
      .text()
  );
}

async function putMetadata(
  body: MetadataCreateBody,
  token: string
): Promise<Result<String>> {
  return runCatching(
    api
      .put(`wenku/metadata/${body.bookId}`, {
        json: body,
        headers: { Authorization: 'Bearer ' + token },
      })
      .text()
  );
}

function createUploadUrl(bookId: string) {
  return `/api/wenku/episode/${bookId}`;
}

export default {
  list,
  getMetadata,
  postMetadata,
  putMetadata,
  createUploadUrl,
};
