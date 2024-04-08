import { AmazonNovel, WenkuVolumeDto } from '@/model/WenkuNovel';

import { Amazon, extractAsin } from './Amazon';

const prettyTitle = (title: string) =>
  title
    .replace(/(【[^【】]*】)/g, '')
    .replace(/(\([^()]*\))/g, '')
    .trim();

const prettyCover = (cover: string) =>
  cover
    .replace('_PJku-sticker-v7,TopRight,0,-50.', '')
    .replace('m.media-amazon.com', 'images-cn.ssl-images-amazon.cn')
    .replace(/\.[A-Z0-9_]+\.jpg$/, '.jpg');

const getNovelFromVolumes = async (volumes: WenkuVolumeDto[]) => {
  const metadata = await getNovelByAsin(volumes[0].asin);
  metadata.volumes = volumes.map((v) => {
    const title = prettyTitle(v.title);
    const cover = prettyCover(v.cover);
    return { asin: v.asin, title, cover };
  });
  return metadata;
};

const getNovelByAsin = async (asin: string): Promise<AmazonNovel> => {
  const product = await Amazon.getProduct(asin);
  if (product.type === 'volume') {
    const volume = product.volume;
    const title = prettyTitle(volume.title);
    const cover = prettyCover(volume.cover);
    return {
      title,
      r18: false,
      authors: volume.authors,
      artists: volume.artists,
      introduction: volume.introduction,
      volumes: [{ asin, title, cover }],
    };
  } else if (product.type === 'serial') {
    const { total } = product.serial;

    const serial = await Amazon.getSerial(asin, total);

    return getNovelFromVolumes(serial.volumes);
  } else {
    return getNovelFromVolumes(product.set.volumes);
  }
};

const getNovelBySearch = async (title: string): Promise<AmazonNovel> => {
  const searchItems = (await Amazon.search(title))
    .filter((it) => it.title.includes(title))
    .sort((a, b) => a.title.localeCompare(b.title));

  const serialAsin = searchItems.map((it) => it.serialAsin).find((it) => it);

  if (serialAsin !== undefined) {
    return getNovelByAsin(serialAsin);
  } else {
    const volumes = searchItems.filter((it) => it.title.includes(title));
    if (volumes.length === 0) throw Error('搜索结果为空');
    return getNovelFromVolumes(volumes);
  }
};

const getNovel = (urlOrQuery: string) => {
  parseImprint('asdf(321)asdf(123)asdg');
  const asin = extractAsin(urlOrQuery);
  if (asin === undefined) {
    return getNovelBySearch(urlOrQuery);
  } else {
    return getNovelByAsin(asin);
  }
};

const parseImprint = (title: string) => {
  const regex = /[【(]([^)】]*)[】)]/g;

  for (const matched of title.matchAll(regex)) {
    const [, imprint] = matched;

    const kanaRegex = /[\u3040-\u30ff]/;
    if (imprint.includes('特典')) {
      continue;
    }
    if (
      imprint.includes('文庫') ||
      imprint.toLowerCase().includes('book') ||
      imprint.toLowerCase().includes('novel') ||
      kanaRegex.test(imprint)
    ) {
      return imprint;
    }
  }
};

const getVolume = async (asin: string) => {
  const product = await Amazon.getProduct(asin);
  if (product.type !== 'volume') {
    throw new Error(`ASIN不对应小说:${asin}`);
  }
  const { title, cover, coverHires, publisher, publishAt } = product.volume;
  return <WenkuVolumeDto>{
    asin,
    title: prettyTitle(title),
    cover: prettyCover(cover),
    coverHires: prettyCover(coverHires),
    publisher,
    imprint: parseImprint(title)?.trim(),
    publishAt,
  };
};

export const AmazonNovelRepository = {
  getNovel,
  getVolume,
  prettyCover,
};
