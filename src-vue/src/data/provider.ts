interface ParseResult {
  providerId: string;
  novelId: string;
}

interface Provider {
  parseUrl(url: string): string | undefined;
  buildMetadataUrl(novelId: string): string;
  buildChapterUrl(novelId: string, chapterId: string): string;
}

const kakuyomu: Provider = {
  parseUrl(url: string): string | undefined {
    return /kakuyomu\.jp\/works\/([0-9]+)/.exec(url)?.[1];
  },
  buildMetadataUrl(novelId: string): string {
    return `https://kakuyomu.jp/works/${novelId}`;
  },
  buildChapterUrl(novelId: string, chapterId: string): string {
    return `https://kakuyomu.jp/works/${novelId}/episodes/${chapterId}`;
  },
};

const syosetu: Provider = {
  parseUrl(url: string): string | undefined {
    return /syosetu\.com\/([A-Za-z0-9]+)/.exec(url)?.[1].toLowerCase();
  },
  buildMetadataUrl(novelId: string): string {
    return `https://ncode.syosetu.com/${novelId}`;
  },
  buildChapterUrl(novelId: string, chapterId: string): string {
    if (chapterId == 'default') {
      return `https://ncode.syosetu.com/${novelId}`;
    } else {
      return `https://ncode.syosetu.com/${novelId}/${chapterId}`;
    }
  },
};

const novelup: Provider = {
  parseUrl(url: string): string | undefined {
    return /novelup\.plus\/story\/([0-9]+)/.exec(url)?.[1];
  },
  buildMetadataUrl(novelId: string): string {
    return `https://novelup.plus/story/${novelId}`;
  },
  buildChapterUrl(novelId: string, chapterId: string): string {
    return `https://novelup.plus/story/${novelId}/${chapterId}`;
  },
};

const hameln: Provider = {
  parseUrl(url: string): string | undefined {
    return /syosetu\.org\/novel\/([0-9]+)/.exec(url)?.[1];
  },
  buildMetadataUrl(novelId: string): string {
    return `https://syosetu.org/novel/${novelId}`;
  },
  buildChapterUrl(novelId: string, chapterId: string): string {
    if (chapterId == 'default') {
      return `https://syosetu.org/novel/${novelId}/`;
    } else {
      return `https://syosetu.org/novel/${novelId}/${chapterId}.html`;
    }
  },
};

const pixiv: Provider = {
  parseUrl(url: string): string | undefined {
    let novelId = /pixiv\.net\/novel\/series\/([0-9]+)/.exec(url)?.[1];
    if (novelId === undefined) {
      novelId = /pixiv\.net\/novel\/show.php\?id=([0-9]+)/.exec(url)?.[1];
      if (novelId !== undefined) {
        novelId = 's' + novelId;
      }
    }
    return novelId;
  },
  buildMetadataUrl(novelId: string): string {
    if (novelId[0] === 's') {
      return `https://www.pixiv.net/novel/show.php?id=${novelId.substring(1)}`;
    } else {
      return `https://www.pixiv.net/novel/series/${novelId}`;
    }
  },
  buildChapterUrl(novelId: string, chapterId: string): string {
    return `https://www.pixiv.net/novel/show.php?id=${chapterId}`;
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
  buildMetadataUrl(novelId: string): string {
    const realNovelId = novelId.replace('-', '/');
    return `https://www.alphapolis.co.jp/novel/${realNovelId}`;
  },
  buildChapterUrl(novelId: string, chapterId: string): string {
    const realNovelId = novelId.replace('-', '/');
    return `https://www.alphapolis.co.jp/novel/${realNovelId}/episode/${chapterId}`;
  },
};

const novelism: Provider = {
  parseUrl(url: string): string | undefined {
    return /novelism\.jp\/novel\/([^\/]+)/.exec(url)?.[1];
  },
  buildMetadataUrl(novelId: string): string {
    return `https://novelism.jp/novel/${novelId}`;
  },
  buildChapterUrl(novelId: string, chapterId: string): string {
    return `https://novelism.jp/novel/${novelId}/article/${chapterId}/`;
  },
};

const providers: { [id: string]: Provider } = {
  kakuyomu,
  syosetu,
  novelup,
  hameln,
  pixiv,
  alphapolis,
  novelism,
};

export function parseUrl(url: string): ParseResult | undefined {
  for (const providerId in providers) {
    const provider = providers[providerId];
    const novelId = provider.parseUrl(url);
    if (novelId !== undefined) {
      return { providerId, novelId };
    }
  }
  return undefined;
}

export function buildMetadataUrl(providerId: string, novelId: string): string {
  return providers[providerId].buildMetadataUrl(novelId);
}

export function buildChapterUrl(
  providerId: string,
  novelId: string,
  chapterId: string
): string {
  return providers[providerId].buildChapterUrl(novelId, chapterId);
}
