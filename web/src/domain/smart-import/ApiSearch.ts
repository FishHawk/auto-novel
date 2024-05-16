import { Locator } from '@/data';
import { extractAsin } from './Common';

const parseSearch = (doc: Document) => {
  const items = Array.from(
    doc.getElementsByClassName('s-search-results')[0].children,
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
          .nextElementSibling?.getElementsByTagName('a') ?? [],
      )
        .map((el) => el.getAttribute('href')!)
        .filter((href) => href.startsWith('/dp/'))
        .map((href) => extractAsin(href))
        .find((asin) => asin);

      return { asin, title, cover, serialAsin };
    });
};

export const search = (query: string) =>
  Locator.amazonRepository().search(query).then(parseSearch);
