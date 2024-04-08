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
  if (doc.getElementsByClassName('series-childAsin-widget').item(0) !== null) {
    return {
      type: 'serial' as 'serial',
      serial: parseProductSerial(doc),
    };
  } else if (doc.getElementsByClassName('bundle-components').item(0) !== null) {
    return {
      type: 'set' as 'set',
      set: parseProductSet(doc),
    };
  } else {
    return {
      type: 'volume' as 'volume',
      volume: parseProductVolume(doc),
    };
  }
};

const parseProductSerial = (doc: Document) => {
  const total =
    doc.getElementById('collection-size')?.textContent?.match(/\d+/)?.[0] ??
    '100';
  return { total };
};

const parseProductSet = (doc: Document) => {
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

  const volumes = Array.from(
    doc.getElementsByClassName('bundle-components')[0].children
  ).map((el) => {
    const img = el.children[0].getElementsByTagName('img')[0];
    const a = el.children[1].children[0].getElementsByTagName('a')[0];

    const asin = extractAsin(a.getAttribute('href')!)!;
    const title = a.text;
    const cover = img.getAttribute('src')!;
    return { asin, title, cover };
  });
  return {
    title,
    authors,
    artists,
    volumes,
  };
};

const parseProductVolume = (doc: Document) => {
  const title = doc.getElementById('productTitle')!.textContent!;
  const subtitle = doc.getElementById('productSubtitle')?.textContent ?? '';
  const r18 = subtitle.includes('成人') || subtitle.includes('アダルト');

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

  const coverHires = doc
    .querySelector('img[data-old-hires]')!
    .getAttribute('data-old-hires')!;

  const getElementContain = (tag: string, content: string) => {
    return doc.evaluate(
      `//${tag}[text()='${content}']`,
      doc,
      null,
      XPathResult.FIRST_ORDERED_NODE_TYPE,
      null
    ).singleNodeValue;
  };

  const getCarouselElement = (label: string) =>
    getElementContain('span', label)?.parentElement?.nextElementSibling
      ?.nextElementSibling?.textContent;

  const getPublisher = () => getCarouselElement('出版社') ?? undefined;

  const getPublishAt = () => {
    const dateStr =
      getCarouselElement('出版日期') ?? getCarouselElement('発売日');
    if (!dateStr) return;

    const regex1 = /(\d+)年 (\d+)月 (\d+)日/; // 2018年 6月 9日
    const regex2 = /(\d+)\/(\d+)\/(\d+)/; // 2018/6/9

    const match = dateStr.match(regex1) ?? dateStr.match(regex2);
    if (match) {
      const [, yearStr, monthStr, dayStr] = match;
      const year = parseInt(yearStr, 10);
      const month = parseInt(monthStr, 10) - 1; // JavaScript月份从0开始
      const day = parseInt(dayStr, 10);
      const date = new Date(year, month, day);
      return date.getTime() / 1000;
    }
    return;
  };

  const publisher = getPublisher()?.trim();
  const publishAt = getPublishAt();

  return {
    title,
    cover,
    coverHires,
    authors,
    artists,
    introduction,
    publisher,
    publishAt,
    r18,
  };
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

  return { authors, artists, volumes };
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
    })
    .filter(({ title }) => {
      // 排除试读，例如：https://www.amazon.co.jp/dp/B0BTDKR5LZ
      return !title.includes('試し読');
    });
};

export const Amazon = {
  getProduct,
  getSerial,
  search,
};
