interface Provider {
  parseUrl(url: string): string | undefined;
  buildNovelUrl(novelId: string): string;
  buildChapterUrl(novelId: string, chapterId: string): string;
}

const kakuyomu: Provider = {
  parseUrl(url: string): string | undefined {
    return /kakuyomu\.jp\/works\/([0-9]+)/.exec(url)?.[1];
  },
  buildNovelUrl(novelId: string): string {
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
  buildNovelUrl(novelId: string): string {
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
  buildNovelUrl(novelId: string): string {
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
  buildNovelUrl(novelId: string): string {
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
  buildNovelUrl(novelId: string): string {
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
  buildNovelUrl(novelId: string): string {
    const realNovelId = novelId.replace('-', '/');
    return `https://www.alphapolis.co.jp/novel/${realNovelId}`;
  },
  buildChapterUrl(novelId: string, chapterId: string): string {
    const realNovelId = novelId.replace('-', '/');
    return `https://www.alphapolis.co.jp/novel/${realNovelId}/episode/${chapterId}`;
  },
};

// 弃用，为了兼容以前的小说暂时保留
const novelism: Provider = {
  parseUrl(url: string): string | undefined {
    return undefined;
  },
  buildNovelUrl(novelId: string): string {
    return `https://novelism.jp/novel/${novelId}`;
  },
  buildChapterUrl(novelId: string, chapterId: string): string {
    return `https://novelism.jp/novel/${novelId}/article/${chapterId}/`;
  },
};

const local: Provider = {
  parseUrl(url: string) {
    return undefined;
  },
  buildNovelUrl(novelId: string): string {
    return '';
  },
  buildChapterUrl(novelId: string, chapterId: string): string {
    return '';
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

  local,
};

export function parseUrl(
  url: string
): { providerId: string; novelId: string } | undefined {
  for (const providerId in providers) {
    const provider = providers[providerId];
    const novelId = provider.parseUrl(url);
    if (novelId !== undefined) {
      return { providerId, novelId };
    }
  }
  return undefined;
}

export function buildWebNovelUrl(providerId: string, novelId: string): string {
  return providers[providerId].buildNovelUrl(novelId);
}

export function buildWebChapterUrl(
  providerId: string,
  novelId: string,
  chapterId: string
): string {
  return providers[providerId].buildChapterUrl(novelId, chapterId);
}

const mapper = [
  ['ハーレム', '后宫'],
  ['シリアス', '严肃'],
  ['ほのぼの', '温暖'],
  ['バトル', '战斗'],
  ['ラブコメ', '爱情喜剧'],
  ['ハッピーエンド', 'HappyEnd'],
  ['バッドエンド', 'BadEnd'],
  ['嘘コク', '假告白'],
  ['ギャグ', '搞笑'],
  ['チート', '作弊'],
  ['ファンタジー', '奇幻'],
  ['スクールラブ', '校园爱情'],
  ['ダーク', '黑暗'],
  ['ミステリー', '推理'],
  ['ヒーロー', '英雄'],
  ['ヒロイン', '女英雄'],
  ['ダンジョン', '迷宫'],
  ['ざまぁ', '活该'],
  ['ざまあ', '活该'],
  ['ディストピア', '反乌托邦'],
  ['アイドル', '偶像'],
  ['シスター', '修女'],
  ['成り上がり', '暴发户'],
  ['ライトノベル', '轻小说'],
  ['セフレ', '性伙伴'],
  ['ホームドラマ', '家庭剧'],
  ['パラレルワールド', '平行世界'],
  ['ヤンデレ', '病娇'],
  ['ツンデレ', '傲娇'],
  ['ゲーム', '游戏'],
  ['コミカライズ', '漫画化'],
  ['アニメ化', '动画化'],
  ['スキル', '技能'],
  ['ボーイズラブ', '耽美'],
  ['ガールズラブ', '百合'],
  ['ドラゴン', '龙'],
  ['いじめ', '欺凌'],
  ['レイプ', '强奸'],
  ['ロリ', '萝莉'],
  ['コメディ', '喜剧'],
  ['カクヨムオンリー', 'Kakuyomu原创'],
];

export function tryTranslateKeyword(keyword: string) {
  mapper.forEach(([jp, zh]) => {
    keyword = keyword.replace(jp, zh);
  });
  return keyword;
}
