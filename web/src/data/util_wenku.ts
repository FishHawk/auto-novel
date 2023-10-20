import ky from 'ky';

import { NovelCreateBody } from './api/api_wenku_novel';
import { runCatching } from './result';

interface WenkuMetadataFetcher {
  parseUrl(url: string): string | undefined;
  fetchMetadata(id: string): Promise<Partial<NovelCreateBody>>;
}

const bangumi: WenkuMetadataFetcher = {
  parseUrl(url: string): string | undefined {
    return /bangumi\.tv\/subject\/([0-9]+)/.exec(url)?.[1];
  },
  async fetchMetadata(id: string): Promise<Partial<NovelCreateBody>> {
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

    const section = await ky
      .get(`https://api.bgm.tv/v0/subjects/${id}`)
      .json<BangumiSection>();

    const metadata: NovelCreateBody = {
      title: section.name,
      titleZh: section.name_cn,
      cover: section.images.medium,
      coverSmall: section.images.small,
      authors: [],
      artists: [],
      keywords: section.tags.map((it) => it.name),
      introduction: section.summary,
    };
    section.infobox.forEach((it) => {
      if (it.key == '作者') {
        metadata.authors.push(it.value);
      } else if (it.key == '插图') {
        metadata.artists.push(it.value);
      }
    });
    return metadata;
  },
};

const amazon: WenkuMetadataFetcher = {
  parseUrl(url: string): string | undefined {
    if (url.indexOf('www.amazon') != -1) {
      return url;
    } else {
      return undefined;
    }
  },
  async fetchMetadata(id: string): Promise<Partial<NovelCreateBody>> {
    const html = await ky.get(id, { credentials: 'include' }).text();
    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');

    const title = doc.getElementById('productTitle')!.textContent!.trim();

    const authors: string[] = [];
    const artists: string[] = [];
    Array.from(doc.getElementsByClassName('author')).forEach((element) => {
      const contribution = element
        .getElementsByClassName('contribution')[0]
        .textContent?.trim();
      const name = element.getElementsByTagName('a')![0].textContent!.trim();
      if (contribution !== undefined) {
        if (contribution.startsWith('(著)')) {
          authors.push(name);
        } else if (contribution.startsWith('(イラスト)')) {
          artists.push(name);
        }
      }
    });

    const introduction = doc
      .getElementById('bookDescription_feature_div')!
      .getElementsByTagName('span')[0]
      .innerHTML.replaceAll('<br>', '\n');

    const coverSmall = doc.getElementById('landingImage')!.getAttribute('src')!;
    const cover = /"hiRes":"([^"]+)",/.exec(html)![1];

    return {
      title,
      cover,
      coverSmall,
      authors,
      artists,
      introduction,
    };
  },
};

const fetchers: { [id: string]: WenkuMetadataFetcher } = {
  bangumi,
  amazon,
};

export function fetchMetadata(url: string) {
  for (const fetcherId in fetchers) {
    const fetcher = fetchers[fetcherId];
    const id = fetcher.parseUrl(url);
    if (id !== undefined) {
      return runCatching(fetcher.fetchMetadata(id));
    }
  }
  return undefined;
}
