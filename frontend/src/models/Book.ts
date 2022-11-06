export type BookStatus = 'queued' | 'started' | 'failed' | 'unknown' | null;

export interface BookFile {
  type: string;
  filename: string;
}

export interface BookFileGroup {
  lang: string;
  type: string;
  status: BookStatus;
  total_episode_number: number;
  cached_episode_number: number;
  files: BookFile[];
}

export interface Book {
  provider_id: string;
  book_id: string;
  url: string,
  title: string;
  files: BookFileGroup[];
}

export function readableLang(lang: string): string {
  if (lang == 'zh') return '中文';
  else if (lang == 'jp') return '日文';
  else return `未知(${lang})`;
}

export function readableStatus(
  status: BookStatus,
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

export function filenameToUrl(filename: string): string {
  return window.location.origin + '/books/' + filename;
}