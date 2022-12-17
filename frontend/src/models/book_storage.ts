import ky from 'ky';
import { Ok, Err, Result } from './util';

export interface BookFileGroup {
  lang: string;
  status: string;
  total: number;
  cached: number;
}

function readableLang(lang: string): string {
  if (lang == 'zh') return '中文';
  else if (lang == 'jp') return '日文';
  else return `未知(${lang})`;
}

function readableStatus(lang: string, total: number, cached: number): string {
  return `${readableLang(lang)}(${cached}/${total})`;
}

export async function getStorage(
  providerId: string,
  bookId: string
): Promise<Result<BookFileGroup[], any>> {
  return ky
    .get(`/api/storage/${providerId}/${bookId}`)
    .json<BookFileGroup[]>()
    .then((it) => {
      for (const group of it) {
        group.status = readableStatus(group.lang, group.total, group.cached);
      }
      return Ok(it);
    })
    .catch((error) => Err(error));
}

export interface BookListItem {
  provider_id: string;
  book_id: string;
  title: string;
  files: BookFileGroup[];
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
          group.status = readableStatus(group.lang, group.total, group.cached);
        }
      }
      return Ok(it);
    })
    .catch((error) => Err(error));
}

const fileTypes = [
  { name: 'TXT', extension: 'txt' },
  { name: 'EPUB', extension: 'epub' },
  { name: '中日对比版TXT', extension: 'mixed.txt' },
  { name: '中日对比版EPUB', extension: 'mixed.epub' },
];

export function getFileTypes(lang: string) {
  return lang === 'jp' ? fileTypes.slice(0, 2) : fileTypes;
}

export function filenameToUrl(
  providerId: string,
  bookId: string,
  lang: string,
  extension: string
): string {
  return (
    window.location.origin +
    `/api/books/${providerId}/${bookId}/${lang}/${extension}`
  );
}
