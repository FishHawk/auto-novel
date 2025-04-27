import { Locator } from '@/data';
import { GenericNovelId } from '@/model/Common';
import { TranslatorId } from '@/model/Translator';

import { ReaderChapter } from '../ReaderStore';

const calculateIndent = (spaces: string): string => {
  let width = 0;
  // assume all spaces are of the same type
  if (spaces.length > 0) {
    if (spaces[0] === '\u3000') {
      width = spaces.length;
    } else if (spaces[0] === ' ') {
      width = spaces.length * 0.5;
    } else {
      width = spaces.length * 0.5;
    }
  }
  return `${width}em`;
};

export type ReaderParagraph =
  | {
      indent?: string;
      text: string;
      source?: string;
      secondary: boolean;
      needSpeak: boolean;
    }
  | { imageUrl: string }
  | undefined;

export const buildParagraphs = (
  gnid: GenericNovelId,
  chapter: ReaderChapter,
): ReaderParagraph[] => {
  const cc = Locator.settingRepository().cc.value;
  const setting = Locator.readerSettingRepository().setting.value;

  const merged: ReaderParagraph[] = [];
  const styles: {
    paragraphs: string[];
    source?: string;
    secondary: boolean;
    needSpeak: boolean;
  }[] = [];
  const needSpeakJp =
    setting.mode === 'jp' || setting.speakLanguages.includes('jp');
  const needSpeakZh =
    setting.mode === 'zh' || setting.speakLanguages.includes('zh');

  if (setting.mode === 'jp') {
    styles.push({
      paragraphs: chapter.paragraphs,
      secondary: false,
      needSpeak: needSpeakJp,
    });
  } else {
    if (setting.mode === 'jp-zh') {
      styles.push({
        paragraphs: chapter.paragraphs,
        secondary: true,
        needSpeak: needSpeakJp,
      });
    }

    const paragraphsWithLabel = (
      t: TranslatorId,
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
            paragraphs: paragraphs.map((it) => cc.toView(it)),
            source: t,
            secondary: false,
            needSpeak: needSpeakZh,
          });
          break;
        } else {
          merged.push({
            text: label + '翻译不存在',
            secondary: true,
            needSpeak: true,
          });
        }
      }
      if (!hasAnyTranslation) {
        return merged;
      }
    } else {
      let i = 0;
      for (const t of setting.translations) {
        const [label, paragraphs] = paragraphsWithLabel(t);
        if (paragraphs) {
          styles.push({
            paragraphs: paragraphs.map((it) => cc.toView(it)),
            source: t,
            secondary: false,
            needSpeak: needSpeakZh && i === 0,
          });
        } else {
          merged.push({
            text: label + '翻译不存在',
            secondary: true,
            needSpeak: true,
          });
        }
        i++;
      }
    }

    if (setting.mode === 'zh-jp') {
      styles.push({
        paragraphs: chapter.paragraphs,
        secondary: true,
        needSpeak: needSpeakJp,
      });
    }
  }

  for (let i = 0; i < chapter.paragraphs.length; i++) {
    const curParagraph = chapter.paragraphs[i];
    if (curParagraph.trim().length === 0) {
      merged.push(undefined);
    } else if (curParagraph.startsWith('<图片>')) {
      merged.push({ imageUrl: curParagraph.slice(4) });
    } else {
      for (const style of styles) {
        let paragraphText = style.paragraphs[i];
        let indent: string | undefined = undefined;
        let text = paragraphText;

        if (!setting.trimLeadingSpaces) {
          const firstCharIndex = paragraphText.search(/\S|$/);
          const leadingSpace = paragraphText.slice(0, firstCharIndex);
          text = paragraphText.slice(firstCharIndex);
          indent = calculateIndent(leadingSpace);
        } else {
          text = paragraphText.trimStart();
        }

        merged.push({
          indent,
          text,
          source:
            setting.enableSourceLabel === true
              ? style.source?.slice(0, 1).toUpperCase()
              : undefined,
          secondary: style.secondary,
          needSpeak: style.needSpeak,
        });
      }
    }
  }
  return merged;
};
