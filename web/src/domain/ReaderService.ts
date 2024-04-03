import { Locator } from '@/data';
import { WebNovelRepository } from '@/data/api';
import { GenericNovelId } from '@/model/Common';
import { ReaderChapter, ReaderParagraph, ReaderTocItem } from '@/model/Reader';
import { TranslatorId } from '@/model/Translator';

export const getToc = async (
  gnid: GenericNovelId
): Promise<ReaderTocItem[]> => {
  if (gnid.type === 'web') {
    return WebNovelRepository.getNovel(gnid.providerId, gnid.novelId).then(
      (it) => it.toc
    );
  } else if (gnid.type === 'wenku') {
    throw '不支持文库';
  } else {
    const repo = await Locator.localVolumeRepository();
    const volume = await repo.getVolume(gnid.volumeId);
    if (volume === undefined) throw Error('小说不存在');
    return volume.toc.map(
      (it) =>
        <ReaderTocItem>{
          titleJp: it.chapterId,
          chapterId: it.chapterId,
        }
    );
  }
};

const getChapter = async (
  gnid: GenericNovelId,
  chapterId: string
): Promise<ReaderChapter> => {
  if (gnid.type === 'web') {
    return WebNovelRepository.getChapter(
      gnid.providerId,
      gnid.novelId,
      chapterId
    );
  } else if (gnid.type === 'wenku') {
    throw '不支持文库';
  } else {
    const repo = await Locator.localVolumeRepository();

    const volumeId = gnid.volumeId;
    const volume = await repo.getVolume(volumeId);
    if (volume === undefined) throw Error('小说不存在');

    const chapter = await repo.getChapter(volumeId, chapterId);
    if (chapter === undefined) throw Error('章节不存在');

    const currIndex = volume.toc.findIndex((it) => it.chapterId == chapterId);
    return <ReaderChapter>{
      titleJp: `${volumeId} - ${chapterId}`,
      titleZh: undefined,
      prevId: volume.toc[currIndex - 1]?.chapterId,
      nextId: volume.toc[currIndex + 1]?.chapterId,
      paragraphs: chapter.paragraphs,
      baiduParagraphs: chapter.baidu?.paragraphs,
      youdaoParagraphs: chapter.youdao?.paragraphs,
      gptParagraphs: chapter.gpt?.paragraphs,
      sakuraParagraphs: chapter.sakura?.paragraphs,
    };
  }
};

const getParagraphs = (
  gnid: GenericNovelId,
  chapter: ReaderChapter
): ReaderParagraph[] => {
  const setting = Locator.readerSettingRepository().ref.value;

  const merged: ReaderParagraph[] = [];
  const styles: {
    paragraphs: string[];
    secondary: boolean;
    popover?: boolean;
  }[] = [];

  if (setting.mode === 'jp') {
    styles.push({ paragraphs: chapter.paragraphs, secondary: false });
  } else {
    if (setting.mode === 'jp-zh') {
      styles.push({ paragraphs: chapter.paragraphs, secondary: true });
    }

    const paragraphsWithLabel = (
      t: TranslatorId
    ): [string, string[] | undefined] => {
      if (t === 'youdao') {
        return ['有道', chapter.youdaoParagraphs];
      } else if (t === 'baidu') {
        return ['百度', chapter.baiduParagraphs];
      } else if (t === 'gpt') {
        return ['GPT', chapter.gptParagraphs];
      } else {
        return ['Sakura', chapter.sakuraParagraphs];
      }
    };
    if (setting.translationsMode === 'priority') {
      let hasAnyTranslation = false;
      for (const t of setting.translations) {
        const [label, paragraphs] = paragraphsWithLabel(t);
        if (paragraphs) {
          hasAnyTranslation = true;
          styles.push({
            paragraphs,
            secondary: false,
            popover:
              setting.enableSakuraReportButton &&
              gnid.type === 'web' &&
              t === 'sakura',
          });
          break;
        } else {
          merged.push({ text: label + '翻译不存在', secondary: true });
        }
      }
      if (!hasAnyTranslation) {
        return merged;
      }
    } else {
      for (const t of setting.translations) {
        const [label, paragraphs] = paragraphsWithLabel(t);
        if (paragraphs) {
          styles.push({
            paragraphs,
            secondary: false,
            popover: setting.enableSakuraReportButton && t === 'sakura',
          });
        } else {
          merged.push({ text: label + '翻译不存在', secondary: true });
        }
      }
    }

    if (setting.mode === 'zh-jp') {
      styles.push({ paragraphs: chapter.paragraphs, secondary: true });
    }
  }

  for (let i = 0; i < chapter.paragraphs.length; i++) {
    if (chapter.paragraphs[i].trim().length === 0) {
      merged.push(null);
    } else if (chapter.paragraphs[i].startsWith('<图片>')) {
      merged.push({ imageUrl: chapter.paragraphs[i].slice(4) });
    } else {
      for (const style of styles) {
        merged.push({
          text: style.paragraphs[i],
          secondary: style.secondary,
          popover: style.popover === true ? i : undefined,
        });
      }
    }
  }
  return merged;
};

export const ReaderService = {
  getToc,
  getChapter,
  getParagraphs,
};
