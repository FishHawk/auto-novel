interface ParseResult {
  providerId: string;
  bookId: string;
}

interface Provider {
  parseUrl(url: string): string | undefined;
  buildMetadataUrl(bookId: string): string;
  buildEpisodeUrl(bookId: string, episodeId: string): string;
}

const kakuyomu: Provider = {
  parseUrl(url: string): string | undefined {
    return /kakuyomu\.jp\/works\/([0-9]+)/.exec(url)?.[1];
  },
  buildMetadataUrl(bookId: string): string {
    return `https://kakuyomu.jp/works/${bookId}`;
  },
  buildEpisodeUrl(bookId: string, episodeId: string): string {
    return `https://kakuyomu.jp/works/${bookId}/episodes/${episodeId}`;
  },
};

const syosetu: Provider = {
  parseUrl(url: string): string | undefined {
    return /syosetu\.com\/([A-Za-z0-9]+)/.exec(url)?.[1].toLowerCase();
  },
  buildMetadataUrl(bookId: string): string {
    return `https://ncode.syosetu.com/${bookId}`;
  },
  buildEpisodeUrl(bookId: string, episodeId: string): string {
    if (episodeId == 'default') {
      return `https://ncode.syosetu.com/${bookId}`;
    } else {
      return `https://ncode.syosetu.com/${bookId}/${episodeId}`;
    }
  },
};

const novelup: Provider = {
  parseUrl(url: string): string | undefined {
    return /novelup\.plus\/story\/([0-9]+)/.exec(url)?.[1];
  },
  buildMetadataUrl(bookId: string): string {
    return `https://novelup.plus/story/${bookId}`;
  },
  buildEpisodeUrl(bookId: string, episodeId: string): string {
    return `https://novelup.plus/story/${bookId}/${episodeId}`;
  },
};

const hameln: Provider = {
  parseUrl(url: string): string | undefined {
    return /syosetu\.org\/novel\/([0-9]+)/.exec(url)?.[1];
  },
  buildMetadataUrl(bookId: string): string {
    return `https://syosetu.org/novel/${bookId}`;
  },
  buildEpisodeUrl(bookId: string, episodeId: string): string {
    if (episodeId == 'default') {
      return `https://syosetu.org/novel/${bookId}/`;
    } else {
      return `https://syosetu.org/novel/${bookId}/${episodeId}.html`;
    }
  },
};

const pixiv: Provider = {
  parseUrl(url: string): string | undefined {
    let bookId = /pixiv\.net\/novel\/series\/([0-9]+)/.exec(url)?.[1];
    if (bookId === undefined) {
      bookId = /pixiv\.net\/novel\/show.php\?id=([0-9]+)/.exec(url)?.[1];
      if (bookId !== undefined) {
        bookId = 's' + bookId;
      }
    }
    return bookId;
  },
  buildMetadataUrl(bookId: string): string {
    if (bookId[0] === 's') {
      return `https://www.pixiv.net/novel/show.php?id=${bookId.substring(1)}`;
    } else {
      return `https://www.pixiv.net/novel/series/${bookId}`;
    }
  },
  buildEpisodeUrl(bookId: string, episodeId: string): string {
    return `https://www.pixiv.net/novel/show.php?id=${episodeId}`;
  },
};

const alphapolis: Provider = {
  parseUrl(url: string): string | undefined {
    const matched = /www\.alphapolis\.co\.jp\/novel\/([0-9]+)\/([0-9]+)/.exec(
      url
    );
    if (matched) {
      return `${matched[1]}-${matched[2]}`;
    } else {
      return undefined;
    }
  },
  buildMetadataUrl(bookId: string): string {
    const realBookId = bookId.replace('-', '/');
    return `https://www.alphapolis.co.jp/novel/${realBookId}`;
  },
  buildEpisodeUrl(bookId: string, episodeId: string): string {
    const realBookId = bookId.replace('-', '/');
    return `https://www.alphapolis.co.jp/novel/${realBookId}/episode/${episodeId}`;
  },
};

const providers: { [id: string]: Provider } = {
  kakuyomu,
  syosetu,
  novelup,
  hameln,
  pixiv,
  alphapolis,
};

export function parseUrl(url: string): ParseResult | undefined {
  for (const providerId in providers) {
    const provider = providers[providerId];
    const bookId = provider.parseUrl(url);
    if (bookId !== undefined) {
      return { providerId, bookId };
    }
  }
  return undefined;
}

export function buildMetadataUrl(providerId: string, bookId: string): string {
  return providers[providerId].buildMetadataUrl(bookId);
}

export function buildEpisodeUrl(
  providerId: string,
  bookId: string,
  episodeId: string
): string {
  return providers[providerId].buildEpisodeUrl(bookId, episodeId);
}
