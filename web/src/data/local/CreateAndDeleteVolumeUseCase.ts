import { v4 as uuidv4 } from 'uuid';

import { Epub, Srt, Txt } from '@/util/file';

import { EpubParserV1 } from './EpubParser';
import { LocalVolumeRepository } from './LocalVolumeRepository';

export const createVolume = async (file: File) => {
  const id = file.name;
  if ((await LocalVolumeRepository.getMetadata(id)) !== undefined) {
    throw Error('小说已经存在');
  }

  const chapters: { chapterId: string; paragraphs: string[] }[] = [];

  if (id.endsWith('.txt')) {
    const content = await Txt.readContent(file);
    const jpLines = content.split('\n');
    const chunkSize = 1000;
    for (let i = 0; i < jpLines.length; i += chunkSize) {
      const paragraphs = jpLines.slice(i, i + chunkSize);
      chapters.push({ chapterId: i.toString(), paragraphs });
    }
  } else if (id.endsWith('.epub')) {
    await Epub.forEachXHtmlFile(file, (path, doc) => {
      const paragraphs = EpubParserV1.extractText(doc);
      chapters.push({ chapterId: path, paragraphs });
    });
  } else if (id.endsWith('.srt')) {
    const subtitles = await Srt.readContent(file);
    const jpLines = subtitles
      .flatMap((it) => it.text)
      .map((it) => Srt.cleanFormat(it));
    chapters.push({ chapterId: '0'.toString(), paragraphs: jpLines });
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
