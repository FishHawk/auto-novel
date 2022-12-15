import ky from 'ky';
import { Ok, Err, Result } from './util';

type UpdateTaskStatus = 'queued' | 'started' | 'failed' | 'unknown' | null;

interface BookFile {
  type: string;
  filename: string;
}

export interface RawBookFileGroup {
  lang: string;
  status: UpdateTaskStatus;
  total_episode_number: number;
  cached_episode_number: number;
  files: BookFile[];
  mixed_files: BookFile[];
}

export interface BookFileGroup {
  langCode: string;
  statusCode: UpdateTaskStatus;
  lang: string;
  status: string;
  files: BookFile[];
  mixedFiles: BookFile[];
}

function readableLang(lang: string): string {
  if (lang == 'zh') return '中文';
  else if (lang == 'jp') return '日文';
  else return `未知(${lang})`;
}

function readableStatus(
  status: UpdateTaskStatus,
  total: number,
  cached: number
): string {
  const page_status = `(${cached}/${total})`;
  if (status == 'queued') return '排队中' + page_status;
  else if (status == 'started') return '更新中' + page_status;
  else if (status == 'failed') return '失败' + page_status;
  else if (status == 'unknown') return '未知' + page_status;
  else {
    if (total > cached) return `不完整(${cached}/${total})`;
    else return `完整(${cached}/${total})`;
  }
}

function processRawFileGroup(it: RawBookFileGroup): BookFileGroup {
  return {
    langCode: it.lang,
    statusCode: it.status,
    lang: readableLang(it.lang),
    status: readableStatus(
      it.status,
      it.total_episode_number,
      it.cached_episode_number
    ),
    files: it.files,
    mixedFiles: it.mixed_files,
  };
}

export async function getStorage(
  providerId: string,
  bookId: string
): Promise<Result<BookFileGroup[], any>> {
  return ky
    .get(`/api/storage/${providerId}/${bookId}`)
    .json<RawBookFileGroup[]>()
    .then((list) => Ok(list.map((it) => processRawFileGroup(it))))
    .catch((error) => Err(error));
}

export async function postStorageTask(
  providerId: string,
  bookId: string,
  lang: string,
  startIndex: number,
  endIndex: number
): Promise<Result<string, any>> {
  return ky
    .post(`/api/storage/${providerId}/${bookId}/${lang}`, {
      searchParams: { startIndex, endIndex },
    })
    .text()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export interface BookListItem {
  provider_id: string;
  book_id: string;
  title: string;
  files: RawBookFileGroup[];
}

export interface BookPagedList {
  total: number;
  books: BookListItem[];
}

export async function getBookPagedList(
  page: number
): Promise<Result<BookPagedList, any>> {
  return ky
    .get('/api/storage-list', { searchParams: { page } })
    .json<BookPagedList>()
    .then((it) => {
      for (const item of it.books) {
        for (const group of item.files) {
          group.lang = readableLang(group.lang);
        }
      }
      return Ok(it);
    })
    .catch((error) => Err(error));
}

export function filenameToUrl(filename: string): string {
  return window.location.origin + '/books/' + filename;
}
