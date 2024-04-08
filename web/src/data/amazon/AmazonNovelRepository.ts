import { AmazonNovel, WenkuVolumeDto } from '@/model/WenkuNovel';

import { Amazon, extractAsin } from './Amazon';

const parseTitle = (title: string) => {
  const irrelevantKeywords = [
    '特典',
    '限定', // 例如：【電子書籍限定書き下ろしSS付き】 https://www.amazon.co.jp/zh/dp/B0CJ2G42MK
  ];

  const imprintKeywords = [
    '文庫',
    'book',
    'novel',
    'ノベル',
    'ブックス',
    //
    '電撃の新文芸',
    'サーガフォレスト',
    'ムーンドロップス',
    '蜜猫ｎｏｖｅｌｓ',
    'ベリーズファンタジー',
    'アルファポリス',
    'アイリスNEO',
    'メリッサ',
    'フォーユー出版',
    '濃蜜ラブルージュ',
  ];

  let imprint;
  const regex = /[【（(]([^)）】]*)[)）】]/g;
  for (const [matched, content] of title.matchAll(regex)) {
    if (irrelevantKeywords.some((it) => content.includes(it))) {
      title = title.replace(matched, '');
    } else if (
      imprintKeywords.some((it) => content.toLocaleLowerCase().includes(it))
    ) {
      title = title.replace(matched, '');
      imprint = content.trim();
    }
  }
  title = title.trim();
  return { title, imprint };
};

const prettyCover = (cover: string) =>
  cover
    .replace('_PJku-sticker-v7,TopRight,0,-50.', '')
    .replace('m.media-amazon.com', 'images-cn.ssl-images-amazon.cn')
    .replace(/\.[A-Z0-9_]+\.jpg$/, '.jpg');

const getNovelFromVolumes = async (volumes: WenkuVolumeDto[]) => {
  const metadata = await getNovelByAsin(volumes[0].asin);
  metadata.volumes = volumes.map((v) => {
    const { title, imprint } = parseTitle(v.title);
    const cover = prettyCover(v.cover);
    return { asin: v.asin, title, cover, imprint };
  });
  return metadata;
};

const getNovelByAsin = async (asin: string): Promise<AmazonNovel> => {
  const product = await Amazon.getProduct(asin);
  if (product.type === 'volume') {
    const volume = product.volume;
    const { title, imprint } = parseTitle(volume.title);
    const cover = prettyCover(volume.cover);
    return {
      title,
      r18: volume.r18,
      authors: volume.authors,
      artists: volume.artists,
      introduction: volume.introduction,
      volumes: [{ asin, title, cover, imprint }],
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
  parseTitle('asdf(321)asdf(123)asdg');
  const asin = extractAsin(urlOrQuery);
  if (asin === undefined) {
    return getNovelBySearch(urlOrQuery);
  } else {
    return getNovelByAsin(asin);
  }
};

const getVolume = async (asin: string) => {
  const product = await Amazon.getProduct(asin);
  if (product.type !== 'volume') {
    throw new Error(`ASIN不对应小说:${asin}`);
  }
  const { title, cover, coverHires, publisher, publishAt } = product.volume;
  const { title: realTitle, imprint } = parseTitle(title);
  return <WenkuVolumeDto>{
    asin,
    title: realTitle,
    cover: prettyCover(cover),
    coverHires: prettyCover(coverHires),
    publisher,
    imprint,
    publishAt,
  };
};

export const AmazonNovelRepository = {
  getNovel,
  getVolume,
  prettyCover,
};
