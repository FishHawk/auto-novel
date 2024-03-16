import { v4 as uuidv4 } from 'uuid';

import { Epub } from '@/util/epub/epub';
import { Txt } from '@/util/epub/txt';

import { LocalVolumeRepository } from './LocalVolumeRepository';

export const createVolume = async (file: File) => {
  const id = file.name;
  const chapters: { chapterId: string; paragraphs: string[] }[] = [];

  if ((await LocalVolumeRepository.getMetadata(id)) !== undefined) {
    throw Error('小说已经存在');
  }

  if (id.endsWith('.txt')) {
    const content = await Txt.readContent(file);
    const jpLines = content.split('\n');
    const chunkSize = 1000;
    for (let i = 0; i < jpLines.length; i += chunkSize) {
      const paragraphs = jpLines.slice(i, i + chunkSize);
      chapters.push({ chapterId: i.toString(), paragraphs });
    }
  } else {
    await Epub.forEachXHtmlFile(file, (path, doc) => {
      const paragraphs = epubParserV1.extractText(doc);
      chapters.push({ chapterId: path, paragraphs });
    });
  }

  for (const { chapterId, paragraphs } of chapters) {
    await LocalVolumeRepository.createChapter({
      id: `${id}/${chapterId}`,
      volumeId: id,
      paragraphs,
    });
  }
  await LocalVolumeRepository.createMetadata({
    id,
    createAt: Date.now(),
    toc: chapters.map((it) => ({
      chapterId: it.chapterId,
    })),
    glossaryId: uuidv4(),
    glossary: {},
  });
  await LocalVolumeRepository.createFile(id, file);
};

export const deleteVolume = (id: string) =>
  Promise.all([
    LocalVolumeRepository.deleteChapterByVolumeId(id),
    LocalVolumeRepository.deleteMetadata(id),
    LocalVolumeRepository.deleteFile(id),
  ]);

interface EpubParser {
  extractText: (doc: Document) => string[];
  injectTranslation: (
    doc: Document,
    lang: 'zh' | 'jp-zh' | 'zh-jp',
    zhLinesList: string[][]
  ) => Document;
}

export const epubParserV1: EpubParser = {
  extractText: (doc: Document) => {
    Array.from(doc.getElementsByTagName('rt')).forEach((node) =>
      node.parentNode!!.removeChild(node)
    );
    return Array.from(doc.body.getElementsByTagName('p'))
      .map((el) => el.innerText)
      .filter((it) => it.trim().length !== 0);
  },
  injectTranslation: (
    doc: Document,
    lang: 'zh' | 'jp-zh' | 'zh-jp',
    zhLinesList: string[][]
  ) => {
    Array.from(doc.body.getElementsByTagName('p'))
      .filter((el) => el.innerText.trim().length !== 0)
      .forEach((el, index) => {
        if (lang === 'zh') {
          zhLinesList.forEach((lines) => {
            const p = document.createElement('p');
            const t = document.createTextNode(lines[index]);
            p.appendChild(t);
            el.parentNode!!.insertBefore(p, el);
          });
          el.parentNode!!.removeChild(el);
        } else if (lang === 'jp-zh') {
          zhLinesList.forEach((lines) => {
            const p = document.createElement('p');
            const t = document.createTextNode(lines[index]);
            p.appendChild(t);
            el.parentNode!!.insertBefore(p, el.nextSibling);
          });
          el.setAttribute('style', 'opacity:0.4;');
        } else {
          zhLinesList.forEach((lines) => {
            const p = document.createElement('p');
            const t = document.createTextNode(lines[index]);
            p.appendChild(t);
            el.parentNode!!.insertBefore(p, el);
          });
          el.setAttribute('style', 'opacity:0.4;');
        }
      });

    return doc;
  },
};
