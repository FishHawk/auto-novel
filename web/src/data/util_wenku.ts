import ky from 'ky';

function extractAsin(url: string) {
  const asinRegex = /(?:[/dp/]|$)([A-Z0-9]{10})/g;
  return asinRegex.exec(url)?.[1];
}

function prettyTitle(title: string) {
  return title
    .replace(/(【[^【】]*】)/g, '')
    .replace(/(\([^()]*\))/g, '')
    .trim();
}

async function getHtml(url: string) {
  const html = await ky.get(url, { credentials: 'include' }).text();
  const parser = new DOMParser();
  const doc = parser.parseFromString(html, 'text/html');
  return doc;
}

interface AmazonMetadata {
  title: string;
  cover: string;
  r18: boolean;
  authors: string[];
  artists: string[];
  introduction: string;
  volumes: Array<{
    asin: string;
    title: string;
    cover: string;
  }>;
}

async function getProductWithAdultCheck(asin: string) {
  const url = `https://www.amazon.co.jp/dp/${asin}`;

  function parseHtml(html: string) {
    const parser = new DOMParser();
    return parser.parseFromString(html, 'text/html');
  }
  function isAgeConfirm(text: string) {
    return ['年龄确认', '年齢確認'].some((word) => text.includes(word));
  }

  const htmlWithoutCookies = await ky.get(url).text();
  if (!isAgeConfirm(htmlWithoutCookies)) {
    return {
      r18: false,
      doc: parseHtml(htmlWithoutCookies),
    };
  }

  const htmlWithCookies = await ky.get(url, { credentials: 'include' }).text();
  if (!isAgeConfirm(htmlWithCookies)) {
    return {
      r18: true,
      doc: parseHtml(htmlWithCookies),
    };
  }

  throw Error('触发年龄限制，请按说明使用插件公开亚马逊Cookies');
}

async function fetchMetadataFromAsin(asin: string): Promise<AmazonMetadata> {
  const { r18, doc } = await getProductWithAdultCheck(asin);
  const serial = doc.getElementsByClassName('series-childAsin-widget').item(0);
  if (serial === null) {
    const title = prettyTitle(doc.getElementById('productTitle')!.textContent!);

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
      r18,
      authors,
      artists,
      introduction,
      volumes: [{ asin, title, cover }],
    };
  } else {
    const total =
      doc.getElementById('collection-size')?.textContent?.match(/\d+/)?.[0] ??
      100;

    const doc2 = await getHtml(
      `https://www.amazon.co.jp/kindle-dbs/productPage/ajax/seriesAsinList?asin=${asin}&pageNumber=1&pageSize=${total}`
    );

    const introduction = doc2
      .getElementsByClassName('a-size-base collectionDescription')
      .item(0)!.textContent!;

    const authorsSet = new Set<string>();
    const artistsSet = new Set<string>();
    Array.from(
      doc2.getElementsByClassName('series-childAsin-item-details-contributor')
    ).forEach((element) => {
      const contribution = element
        .textContent!.trim()
        .replace(/(,)$/, '')
        .trim();
      if (contribution.endsWith('(著)')) {
        authorsSet.add(contribution.replace(/(\(著\))$/, '').trim());
      } else if (contribution.endsWith('(イラスト)')) {
        artistsSet.add(contribution.replace(/(\(イラスト\))$/, '').trim());
      }
    });

    const authors = [...authorsSet].filter((it) => !artistsSet.has(it));
    const artists = [...artistsSet];

    const volumes = Array.from(
      doc2.getElementById('series-childAsin-batch_1')!.children
    ).map((it) => {
      const titleLink = it.getElementsByClassName(
        'a-size-base-plus a-link-normal itemBookTitle a-text-bold'
      )[0]!;
      const asin = extractAsin(titleLink.getAttribute('href')!)!;
      const title = prettyTitle(titleLink.textContent!);
      const cover = it
        .getElementsByTagName('img')[0]
        .getAttribute('src')!
        .replace('_PJku-sticker-v7,TopRight,0,-50.', '');
      return { asin, title, cover };
    });

    return {
      title: volumes[0].title,
      cover: volumes[0].cover,
      r18,
      authors,
      artists,
      introduction,
      volumes,
    };
  }
}

async function fetchMetadataFromSearch(title: string): Promise<AmazonMetadata> {
  title = title.trim();
  async function search(query: string) {
    const doc = await getHtml(
      `https://www.amazon.co.jp/s?k=${query}&rh=n%3A465392&i=stripbooks`
    );
    return Array.from(
      doc.getElementsByClassName('s-search-results')[0].children
    )
      .filter((it) => it.getAttribute('data-asin'))
      .map((it) => {
        const asin = it.getAttribute('data-asin')!;
        const title = prettyTitle(
          it.getElementsByTagName('h2')[0].textContent!
        );
        const cover = it.getElementsByTagName('img')[0].getAttribute('src')!;
        return { asin, title, cover };
      });
  }

  const volumes = (await search(title + ' 小説'))
    .filter((it) => it.title.includes(title))
    .sort((a, b) => a.title.localeCompare(b.title));

  if (volumes.length === 0) throw Error('搜索结果为空');

  const metadata = await fetchMetadataFromAsin(volumes[0].asin);
  metadata.volumes = volumes;
  metadata.cover = volumes[0].cover;
  return metadata;
}

export function fetchMetadata(query: string) {
  const asin = extractAsin(query);
  if (asin === undefined) {
    return fetchMetadataFromSearch(query);
  } else {
    return fetchMetadataFromAsin(asin);
  }
}
