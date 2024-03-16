import { LocalVolumeRepository } from './LocalVolumeRepository';

interface ReadableChapter {
  titleJp: string;
  titleZh?: string;
  prevId?: string;
  nextId?: string;
  paragraphs: string[];
  baiduParagraphs?: string[];
  youdaoParagraphs?: string[];
  gptParagraphs?: string[];
  sakuraParagraphs?: string[];
}

export const getReadableChapter = async (id: string, chapterId: string) => {
  const metadata = await LocalVolumeRepository.getMetadata(id);
  if (metadata === undefined) throw Error('小说不存在');

  const chapter = await LocalVolumeRepository.getChapter(id, chapterId);
  if (chapter === undefined) throw Error('章节不存在');

  const currIndex = metadata.toc.findIndex((it) => it.chapterId == chapterId);
  return <ReadableChapter>{
    titleJp: `${id} - ${chapterId}`,
    titleZh: undefined,
    prevId: metadata.toc[currIndex - 1]?.chapterId,
    nextId: metadata.toc[currIndex + 1]?.chapterId,
    paragraphs: chapter.paragraphs,
    baiduParagraphs: chapter.baidu?.paragraphs,
    youdaoParagraphs: chapter.youdao?.paragraphs,
    gptParagraphs: chapter.gpt?.paragraphs,
    sakuraParagraphs: chapter.sakura?.paragraphs,
  };
};
