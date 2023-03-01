export interface UpdateProgress {
  name: string;
  total: number | undefined;
  finished: number;
  error: number;
}
