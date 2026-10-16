import type { WebTranslateTask } from '@/model/Translator';

/** 元数据（标题、简介、目录）翻译前的编码与翻译后的还原 */
export const createWebMetadataCoder = (
  task: WebTranslateTask,
  forceMetadata: boolean,
) => {
  const encoded: string[] = [];
  if (!task.titleZh) {
    encoded.push(task.titleJp);
  }
  if (!task.introductionZh) {
    encoded.push(task.introductionJp);
  }
  const toc = task.toc
    .filter((it) => forceMetadata || !it.titleZh)
    .map((it) => it.titleJp);

  const tocWordsDict: { [key: string]: string } = {};
  const tocWords = Array.from(
    new Set(
      toc
        .map((it) => it.split(/[\s　]+/))
        .filter((it) => it.length > 0)
        .flat(),
    ),
  );

  const preTranslateTocWord = (wordJp: string) => {
    if (/^第?[０-９0-9]+話?$/.test(wordJp)) {
      return wordJp.replace('話', '话');
    } else if (wordJp === '閑話') {
      return '闲话';
    } else if (wordJp === '幕間') {
      return '幕间';
    }
  };
  const tocWordsNeedTranslate = tocWords.filter((wordJp) => {
    const wordZh = preTranslateTocWord(wordJp);
    if (wordZh !== undefined) {
      tocWordsDict[wordJp] = wordZh;
      return false;
    } else {
      return true;
    }
  });
  encoded.push(...tocWordsNeedTranslate);

  const recover = (translated: string[]) => {
    const obj: {
      title?: string;
      introduction?: string;
      toc: { [key: string]: string };
    } = { toc: {} };

    if (!task.titleZh) {
      obj.title = translated.shift();
    }
    if (!task.introductionZh) {
      obj.introduction = translated.shift();
    }
    for (const wordJp of tocWordsNeedTranslate) {
      tocWordsDict[wordJp] = translated.shift()!;
    }

    new Set(toc).forEach((it) => {
      const spaces = it.split(/[^\s　]+/).filter((it) => it.length > 0);
      obj.toc[it] = it
        .split(/[\s　]+/)
        .map((it) => {
          if (it.length === 0) {
            return [spaces.shift() ?? ''];
          } else {
            return [tocWordsDict[it], spaces.shift() ?? ''];
          }
        })
        .flat()
        .join('');
    });
    return obj;
  };

  return {
    encoded,
    needUpload: encoded.length > 0 || toc.length > 0,
    recover,
  };
};
