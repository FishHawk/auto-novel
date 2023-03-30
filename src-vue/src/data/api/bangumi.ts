import ky from 'ky';
import { Result, runCatching } from './result';

interface BangumiSection {
  name: string;
  name_cn: string;
  images: {
    common: string;
    grid: string;
    large: string;
    medium: string;
    small: string;
  };
  infobox: { key: string; value: string }[];
  summary: string;
  tags: { name: string; count: number }[];
}

async function getSection(bookId: string): Promise<Result<BangumiSection>> {
  return runCatching(ky.get(`https://api.bgm.tv/v0/subjects/${bookId}`).json());
}

export default {
  getSection,
};
