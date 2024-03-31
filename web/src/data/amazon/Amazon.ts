import ky, { Options } from 'ky';

export const extractAsin = (url: string) => {
  const asinRegex = /(?:[/dp/]|$)([A-Z0-9]{10})/g;
  return asinRegex.exec(url)?.[1];
};

const getHtml = async (url: string, options?: Options) => {
  const response = await ky.get(url, {
    prefixUrl: 'https://www.amazon.co.jp',
    redirect: 'manual',
    credentials: 'include',
    retry: 0,
    ...options,
  });

  if (response.status === 404) {
    throw Error('小说不存在，请删除cookie并使用日本IP重试');
  } else if (response.status === 0) {
    throw Error('触发年龄限制，请按说明使用插件');
  } else if (!response.ok) {
    throw Error(`未知错误，${response.status}`);
  }
  const html = await response.text();
  const parser = new DOMParser();
  const doc = parser.parseFromString(html, 'text/html');
  return doc;
};

// 获取商品页面，有小说和系列两种类型
const getProduct = (asin: string) => getHtml(`dp/${asin}`).then(parseProduct);

const parseProduct = (doc: Document) => {
  const serial = doc.getElementsByClassName('series-childAsin-widget').item(0);
  if (serial === null) {
    return {
      type: 'volume' as 'volume',
      volume: parseProductVolume(doc),
    };
  } else {
    return {
      type: 'serial' as 'serial',
      serial: parseProductSerial(doc),
    };
  }
};

const parseProductVolume = (doc: Document) => {
  const title = doc.getElementById('productTitle')!.textContent!;

  const authors: string[] = [];
  const artists: string[] = [];
  Array.from(doc.getElementsByClassName('author')).forEach((element) => {
    const contribution = element
      .getElementsByClassName('contribution')[0]
      .textContent?.trim()
      ?.replace(/,$/, '');
    const name = element.getElementsByTagName('a')![0].textContent!.trim();
    if (contribution !== undefined) {
      if (contribution.endsWith('(著)')) {
        authors.push(name);
      } else if (contribution.endsWith('(イラスト)')) {
        artists.push(name);
      }
    }
  });

  const introduction =
    doc
      .getElementById('bookDescription_feature_div')
      ?.getElementsByTagName('span')?.[0]
      ?.innerHTML?.replaceAll('<br>', '\n') ?? '';

  const cover = doc.getElementById('landingImage')!.getAttribute('src')!;

  return {
    title,
    cover,
    authors,
    artists,
    introduction,
  };
};

const parseProductSerial = (doc: Document) => {
  const total =
    doc.getElementById('collection-size')?.textContent?.match(/\d+/)?.[0] ??
    '100';
  return { total };
};

// 获取系列页面
const getSerial = (asin: string, total: string) =>
  getHtml('kindle-dbs/productPage/ajax/seriesAsinList', {
    searchParams: {
      asin,
      pageNumber: 1,
      pageSize: total,
    },
  }).then(parseSerial);

const parseSerial = (doc: Document) => {
  const introduction = doc
    .getElementsByClassName('a-size-base collectionDescription')
    .item(0)!.textContent!;

  const authorsSet = new Set<string>();
  const artistsSet = new Set<string>();
  doc.querySelectorAll('[data-action="a-popover"]').forEach((element) => {
    element
      .getAttribute('data-a-popover')!
      .split('\\n')
      .map((it) => it.trim())
      .forEach((contribution) => {
        if (contribution.endsWith('(著)')) {
          authorsSet.add(contribution.replace(/(\(著\))$/, '').trim());
        } else if (contribution.endsWith('(イラスト)')) {
          artistsSet.add(contribution.replace(/(\(イラスト\))$/, '').trim());
        }
      });
  });
  Array.from(
    doc.getElementsByClassName('series-childAsin-item-details-contributor')
  ).forEach((element) => {
    const contribution = element.textContent!.trim().replace(/(,)$/, '').trim();
    if (contribution.endsWith('(著)')) {
      authorsSet.add(contribution.replace(/(\(著\))$/, '').trim());
    } else if (contribution.endsWith('(イラスト)')) {
      artistsSet.add(contribution.replace(/(\(イラスト\))$/, '').trim());
    }
  });

  const authors = [...authorsSet].filter((it) => !artistsSet.has(it));
  const artists = [...artistsSet];

  const volumes = Array.from(
    doc.getElementById('series-childAsin-batch_1')!.children
  ).map((it) => {
    const titleLink = it.getElementsByClassName(
      'a-size-base-plus a-link-normal itemBookTitle a-text-bold'
    )[0]!;
    const asin = extractAsin(titleLink.getAttribute('href')!)!;
    const title = titleLink.textContent!;
    const cover = it.getElementsByTagName('img')[0].getAttribute('src')!;
    return { asin, title, cover };
  });

  return {
    authors,
    artists,
    introduction,
    volumes,
  };
};

// 获取搜索页面
const search = (query: string) =>
  getHtml('s', {
    searchParams: {
      k: query,
      i: 'stripbooks',
    },
  }).then(parseSearch);

const parseSearch = (doc: Document) => {
  const items = Array.from(
    doc.getElementsByClassName('s-search-results')[0].children
  );
  return items
    .filter((item) => {
      if (!item.getAttribute('data-asin')) {
        return false;
      }

      // 排除漫画
      if (
        Array.from(item.getElementsByTagName('a'))
          .map((el) => el.text)
          .some((text) => text === 'コミック (紙)' || text === 'コミック')
      ) {
        return false;
      }

      return true;
    })
    .map((it) => {
      const asin = it.getAttribute('data-asin')!;
      const title = it.getElementsByTagName('h2')[0].textContent!;
      const cover = it.getElementsByTagName('img')[0].getAttribute('src')!;

      const serialAsin = Array.from(
        it
          .getElementsByTagName('h2')[0]
          .nextElementSibling?.getElementsByTagName('a') ?? []
      )
        .map((el) => el.getAttribute('href')!)
        .filter((href) => href.startsWith('/dp/'))
        .map((href) => extractAsin(href))
        .find((asin) => asin);

      return { asin, title, cover, serialAsin };
    });
};

export const Amazon = {
  getProduct,
  getSerial,
  search,
};
