import { Epub } from '@/util/epub/epub';

import { epubParserV1 } from './CreateAndDeleteVolumeUseCase';
import { LocalVolumeRepository } from './LocalVolumeRepository';

export const getTranslationFile = async ({
  id,
  lang,
  translationsMode,
  translations,
}: {
  id: string;
  lang: 'zh' | 'zh-jp' | 'jp-zh';
  translationsMode: 'parallel' | 'priority';
  translations: ('sakura' | 'baidu' | 'youdao' | 'gpt')[];
}) => {
  const filename = [
    lang,
    (translationsMode === 'parallel' ? 'B' : 'Y') +
      translations.map((it) => it[0]).join(''),
    id,
  ].join('.');

  const metadata = await LocalVolumeRepository.getMetadata(id);
  if (metadata === undefined) throw Error('小说不存在');

  const getZhLinesList = async (chapterId: string) => {
    const chapter = await LocalVolumeRepository.getChapter(id, chapterId);
    if (chapter === undefined) throw Error('章节不存在');

    const jpLines = chapter.paragraphs;
    const zhLinesList: Array<Array<string>> = [];

    for (const id of translations) {
      const zhLine = chapter[id]?.paragraphs;
      if (zhLine !== undefined) zhLinesList.push(zhLine);
    }

    if (translationsMode === 'priority' && zhLinesList.length > 1) {
      zhLinesList.length = 1;
    }

    return { jpLines, zhLinesList };
  };

  if (id.endsWith('.txt')) {
    const buffer = [];
    for (const { chapterId } of metadata.toc) {
      const { jpLines, zhLinesList } = await getZhLinesList(chapterId);

      if (zhLinesList.length === 0) {
        buffer.push('// 该分段翻译缺失。');
      } else {
        const combinedLinesList = zhLinesList;
        if (lang === 'jp-zh') {
          combinedLinesList.unshift(jpLines);
        } else if (lang === 'zh-jp') {
          combinedLinesList.push(jpLines);
        }
        for (let i = 0; i < combinedLinesList[0].length; i++) {
          combinedLinesList.forEach((lines) => buffer.push(lines[i]));
        }
      }
    }
    return {
      filename,
      blob: new Blob([buffer.join('\n')], {
        type: 'text/plain',
      }),
    };
  } else {
    const file = await LocalVolumeRepository.getFile(id);
    if (file === undefined) throw Error('原始文件不存在');

    const parseXHtmlBlob = async (blob: Blob) => {
      const text = await blob.text();
      const parser = new DOMParser();
      const doc = parser.parseFromString(text, 'application/xhtml+xml');
      return doc;
    };
    const generated = await Epub.modify(file.file, async (path, blobIn) => {
      if (metadata.toc.some((it) => it.chapterId === path)) {
        const doc = await parseXHtmlBlob(blobIn);
        const { zhLinesList } = await getZhLinesList(path);
        if (zhLinesList.length === 0) return blobIn;
        await epubParserV1.injectTranslation(doc, lang, zhLinesList);
        return new Blob([doc.documentElement.outerHTML], {
          type: 'text/plain',
        });
      } else if (path.endsWith('opf')) {
        const doc = await parseXHtmlBlob(blobIn);
        // 防止部分阅读器使用竖排
        doc
          .getElementsByTagName('spine')
          .item(0)
          ?.removeAttribute('page-progression-direction');
        return new Blob([doc.documentElement.outerHTML], {
          type: 'text/plain',
        });
      } else if (path.endsWith('css')) {
        return new Blob([''], { type: 'text/plain' });
      } else {
        return blobIn;
      }
    });
    return { filename, blob: generated };
  }
};
