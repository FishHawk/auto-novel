import { AmazonNovel } from '@/model/WenkuNovel';
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

const getNovelByAsin = async (asin: string): Promise<AmazonNovel> => {
  const product = await Amazon.getProduct(asin);
  if (product.type === 'volume') {
    const volume = product.volume;
    const title = prettyTitle(volume.title);
    const cover = prettyCover(volume.cover);
    return {
      title,
      cover,
      r18: false,
      authors: volume.authors,
      artists: volume.artists,
      introduction: volume.introduction,
      volumes: [{ asin, title, cover }],
    };
  } else if (product.type === 'serial') {
    const { total } = product.serial;

    const serial = await Amazon.getSerial(asin, total);

    const volumes = serial.volumes.map((v) => {
      const title = prettyTitle(v.title);
      const cover = prettyCover(v.cover);
      return { asin: v.asin, title, cover };
    });

    const metadata = await getNovelByAsin(volumes[0].asin);
    metadata.volumes = volumes;
    return metadata;
  } else {
    const volumes = product.set.volumes.map((v) => {
      return {
        asin: v.asin,
        title: prettyTitle(v.title),
        cover: prettyCover(v.cover),
      };
    });

    const metadata = await getNovelByAsin(volumes[0].asin);
    metadata.volumes = volumes;
    return metadata;
  }
};

const getNovelBySearch = async (title: string): Promise<AmazonNovel> => {
  const searchItems = (await Amazon.search(title))
    .filter((it) => it.title.includes(title))
    .sort((a, b) => a.title.localeCompare(b.title));

  const serialAsin = searchItems.map((it) => it.serialAsin).find((it) => it);

  if (serialAsin !== undefined) {
    return getNovelByAsin(serialAsin);
  }

  const volumes = searchItems
    .map((v) => {
      return {
        asin: v.asin,
        title: prettyTitle(v.title),
        cover: prettyCover(v.cover),
      };
    })
    .filter((it) => it.title.includes(title));

  if (volumes.length === 0) throw Error('搜索结果为空');

  const metadata = await getNovelByAsin(volumes[0].asin);
  metadata.volumes = volumes;
  return metadata;
};

const getNovel = (urlOrQuery: string) => {
  const asin = extractAsin(urlOrQuery);
  if (asin === undefined) {
    return getNovelBySearch(urlOrQuery);
  } else {
    return getNovelByAsin(asin);
  }
};

export const AmazonNovelRepository = {
  getNovel,
  prettyCover,
};
