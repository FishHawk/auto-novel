import api from './api';
import { Ok, Err, Result } from './result';

interface BookMetadataPatchDto {
  title?: string;
  introduction?: string;
  toc: { [key: number]: string };
}

interface BookEpisodePatchDto {
  paragraphs: { [key: number]: string };
}

async function postMetadataPatch(
  providerId: string,
  bookId: string,
  patch: BookMetadataPatchDto
): Promise<Result<string>> {
  return api
    .post(`novel-edit/metadata/${providerId}/${bookId}`, {
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
  patch: BookEpisodePatchDto
): Promise<Result<string>> {
  return api
    .post(`novel-edit/episode/${providerId}/${bookId}/${episodeId}`, {
      json: patch,
    })
    .text()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export default {
  postMetadataPatch,
  postEpisodePatch,
};
