import { Epub } from '@/util/file/epub';

import { EpubParserV1 } from './EpubParser';
import { LocalVolumeRepository } from './LocalVolumeRepository';
import { Srt, Txt } from '@/util/file';

export const getTranslationFile = async ({
  id,
  mode,
  translationsMode,
  translations,
}: {
  id: string;
  mode: 'zh' | 'zh-jp' | 'jp-zh';
  translationsMode: 'parallel' | 'priority';
  translations: ('sakura' | 'baidu' | 'youdao' | 'gpt')[];
}) => {
  const filename = [
    mode,
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
        if (mode === 'jp-zh') {
          combinedLinesList.unshift(jpLines);
        } else if (mode === 'zh-jp') {
          combinedLinesList.push(jpLines);
        }
        for (let i = 0; i < combinedLinesList[0].length; i++) {
          combinedLinesList.forEach((lines) => buffer.push(lines[i]));
        }
      }
    }
    return {
      filename,
      blob: Txt.writeContent(buffer),
    };
  } else if (id.endsWith('.epub')) {
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
        await EpubParserV1.injectTranslation(doc, mode, zhLinesList);
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
    return {
      filename,
      blob: generated,
    };
  } else if (id.endsWith('.srt')) {
    const file = await LocalVolumeRepository.getFile(id);
    if (file === undefined) throw Error('原始文件不存在');

    const { zhLinesList } = await getZhLinesList('0');

    const subtitles = await Srt.readContent(file.file);
    const newSubtitles: typeof subtitles = [];
    for (const s of subtitles) {
      let texts: string[][] = [];
      for (const zhLines of zhLinesList) {
        texts.push(zhLines.slice(0, s.text.length));
        zhLines.splice(0, s.text.length);
      }

      if (mode === 'jp-zh') {
        texts.unshift(s.text);
      } else if (mode === 'zh-jp') {
        texts.push(s.text);
      }

      for (const text of texts) {
        newSubtitles.push({
          id: (newSubtitles.length + 1).toString(),
          time: s.time,
          text,
        });
      }
    }

    return {
      filename,
      blob: Srt.writeContent(newSubtitles),
    };
  }
  throw new Error('不支持的文件格式');
};
