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
  title: string;
  files: BookFileGroup[];
}
