import type { Glossary } from '@auto-novel/translator';
import type { ChapterMeta } from '../TaskState';
import type { TranslateTaskParams, WebTranslateTask } from '@/model/Translator';
import type { ChapterDetail, TranslationTask, TranslatorId } from './types';
import { WebNovelApi } from '@/api';
import { createWebMetadataCoder } from '@/domain/translate/WebMetadataCoder';
import { buildChapterMetaList } from './utils';

export class WebTranslationTask implements TranslationTask {
  readonly type = 'web' as const;
  readonly description: string;
  readonly level: 'normal' | 'expire' | 'all' | 'sync';
  chapters: ChapterMeta[] = [];
  glossary: Glossary = {};
  glossaryId = '';
  initialized = false;

  private api!: ReturnType<typeof WebNovelApi.createTranslationApi>;
  private params: TranslateTaskParams;
  private task?: WebTranslateTask;

  constructor(
    private providerId: string,
    private novelId: string,
    private translatorId: TranslatorId,
    params: TranslateTaskParams,
  ) {
    this.description = `web/${providerId}/${novelId}`;
    this.level = params.level;
    this.params = params;
  }

  async initMeta(signal?: AbortSignal): Promise<void> {
    this.api = WebNovelApi.createTranslationApi(
      this.providerId,
      this.novelId,
      this.translatorId,
      this.params.level === 'sync',
      signal,
    );

    const task = await this.api.getTranslateTask();
    this.task = task;
    this.glossary = task.glossary;
    this.glossaryId = task.glossaryUuid;

    const { startIndex, endIndex, level } = this.params;

    const validToc = task.toc
      .filter((item) => item.chapterId !== undefined)
      .slice(startIndex, endIndex);

    const isDone = (item: WebTranslateTask['toc'][number]) =>
      item.glossaryUuid !== undefined;
    const isExpired = (item: WebTranslateTask['toc'][number]) =>
      item.glossaryUuid !== undefined &&
      item.glossaryUuid !== task.glossaryUuid;
    const toMeta = (item: WebTranslateTask['toc'][number], _order: number) => ({
      chapterId: item.chapterId!,
      title: item.titleJp,
    });

    this.chapters = buildChapterMetaList(
      validToc,
      startIndex,
      level,
      isDone,
      isExpired,
      toMeta,
    );
    this.initialized = true;
  }

  get needsMetadata(): boolean {
    return (
      this.initialized &&
      this.translatorId === 'sakura' &&
      this.task !== undefined &&
      createWebMetadataCoder(this.task, this.params.forceMetadata).needUpload
    );
  }

  /** 翻译标题、简介和目录，与旧版工作区一致 */
  async translateMetadata(
    translate: (texts: string[]) => Promise<string[]>,
    signal?: AbortSignal,
    canUpload?: () => boolean,
  ): Promise<void> {
    if (!this.needsMetadata || !this.task) return;
    const coder = createWebMetadataCoder(this.task, this.params.forceMetadata);
    const checkUpload = () => {
      signal?.throwIfAborted();
      if (canUpload?.() === false) {
        throw new Error('存在未通过上传检查的翻译器，无法启动网络／文库任务');
      }
    };
    checkUpload();
    const translated =
      coder.encoded.length === 0 ? [] : await translate(coder.encoded);
    checkUpload();
    if (translated.length !== coder.encoded.length) {
      throw new Error('元数据翻译结果数量不匹配');
    }
    // initMeta 的请求可能早于本轮运行，上传必须绑定本轮的取消信号。
    const api = WebNovelApi.createTranslationApi(
      this.providerId,
      this.novelId,
      this.translatorId,
      this.params.level === 'sync',
      signal,
    );
    await api.updateMetadataTranslation(coder.recover(translated));
  }

  async fetchChapter(chapterId: string): Promise<ChapterDetail> {
    const chapterTask = await this.api.getChapterTranslateTask(chapterId);
    return {
      paragraphs: chapterTask.paragraphJp,
      glossary: chapterTask.glossary,
      glossaryId: chapterTask.glossaryId,
      oldParagraphZh: chapterTask.oldParagraphZh ?? null,
      oldGlossary: chapterTask.oldGlossary,
    };
  }

  async uploadChapter(
    chapterId: string,
    glossaryId: string,
    translatedParagraphs: string[],
  ): Promise<void> {
    await this.api.updateChapterTranslation(chapterId, {
      glossaryId,
      paragraphsZh: translatedParagraphs,
    });
  }
}
