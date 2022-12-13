interface ParseResult {
  providerId: string;
  bookId: string;
}

function parseUrlKakuyomu(url: string): string | undefined {
  return /kakuyomu\.jp\/works\/([0-9]+)/.exec(url)?.[1];
}

function parseUrlSyosetu(url: string): string | undefined {
  return /syosetu\.com\/([A-Za-z0-9]+)/.exec(url)?.[1].toLowerCase();
}

function parseUrlNovelup(url: string): string | undefined {
  return /novelup\.plus\/story\/([0-9]+)/.exec(url)?.[1];
}

function parseUrlHameln(url: string): string | undefined {
  return /syosetu\.org\/novel\/([0-9]+)/.exec(url)?.[1];
}

function parseUrlPixiv(url: string): string | undefined {
  let bookId = /pixiv\.net\/novel\/series\/([0-9]+)/.exec(url)?.[1];
  if (bookId === undefined) {
    bookId = /pixiv\.net\/novel\/show.php\?id=([0-9]+)/.exec(url)?.[1];
    if (bookId !== undefined) {
      bookId = 's' + bookId;
    }
  }
  return bookId;
}

export function parseUrl(url: string): ParseResult | undefined {
  const funcs: { [id: string]: (url: string) => string | undefined } = {
    kakuyomu: parseUrlKakuyomu,
    syosetu: parseUrlSyosetu,
    novelup: parseUrlNovelup,
    hameln: parseUrlHameln,
    pixiv: parseUrlPixiv,
  };
  for (const providerId in funcs) {
    const func = funcs[providerId];
    const bookId = func(url);
    if (bookId !== undefined) {
      return { providerId, bookId };
    }
  }
  return undefined;
}
