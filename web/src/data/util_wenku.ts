import ky from 'ky';

import { NovelCreateBody } from './api/api_wenku_novel';
import { Ok, runCatching } from './result';

const getNovelFromBangumi = async (novelId: string) => {
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

  const sectionResult = await runCatching(
    ky.get(`https://api.bgm.tv/v0/subjects/${novelId}`).json<BangumiSection>()
  );
  if (sectionResult.ok) {
    const metadata: NovelCreateBody = {
      title: sectionResult.value.name,
      titleZh: sectionResult.value.name_cn,
      cover: sectionResult.value.images.medium,
      coverSmall: sectionResult.value.images.small,
      authors: [],
      artists: [],
      keywords: sectionResult.value.tags.map((it) => it.name),
      introduction: sectionResult.value.summary,
    };
    sectionResult.value.infobox.forEach((it) => {
      if (it.key == '作者') {
        metadata.authors.push(it.value);
      } else if (it.key == '插图') {
        metadata.artists.push(it.value);
      }
    });
    return Ok(metadata);
  } else {
    return sectionResult;
  }
};

export const WenkuMetadataFetcher = {
  getNovelFromBangumi,
};
