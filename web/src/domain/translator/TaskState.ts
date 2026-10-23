import type { SegmentTracker, LineRange } from '@auto-novel/translator';

export type ChapterStatus =
  | 'pending'
  | 'translating'
  | 'paused'
  | 'done'
  | 'error';
export type SegmentStatus = 'pending' | 'translating' | 'done' | 'error';

export interface ChapterMeta {
  chapterId: string;
  title: string;
  order: number;
  status: ChapterStatus;
  segmentProgress?: { completed: number; total: number };
}

export interface SegmentInfo {
  status: SegmentStatus;
  lines: string[];
  translatedLines: string[];
  error: any;
  translatorId?: string;
}

export class ChapterSegmentState implements SegmentTracker {
  readonly chapterId: string;
  ranges: LineRange[] = [];
  segments: SegmentInfo[] = [];
  /** 是否完成 Segment 切分 */
  ready = false;
  /** 已完成章节可释放正文，预览时按需重新读取。 */
  textReleased = false;

  get allDone(): boolean {
    return (
      this.segments.length > 0 &&
      this.segments.every((s) => s.status === 'done')
    );
  }
  get completedCount(): number {
    return this.segments.filter((s) => s.status === 'done').length;
  }
  get translatingCount(): number {
    return this.segments.filter((s) => s.status === 'translating').length;
  }
  get errorCount(): number {
    return this.segments.filter((s) => s.status === 'error').length;
  }

  constructor(chapterId: string) {
    this.chapterId = chapterId;
    return reactive(this) as ChapterSegmentState;
  }

  get finished(): boolean {
    return (
      this.segments.length > 0 &&
      this.segments.every(
        (segment) => segment.status === 'done' || segment.status === 'error',
      )
    );
  }

  onSegmentsReady(lines: string[], ranges: LineRange[]): void {
    this.ranges = ranges;
    this.segments = ranges.map((range) => ({
      status: 'pending',
      lines: lines.slice(range.start, range.end),
      translatedLines: [],
      error: null,
    }));
    this.ready = true;
    this.textReleased = false;
  }

  onSegStart(segmentOrder: number, translatorId: string): void {
    const seg = this.segments[segmentOrder];
    if (seg) {
      seg.status = 'translating';
      seg.translatorId = translatorId;
    }
  }

  onSegComplete(segmentOrder: number, translatedLines: string[]): void {
    const seg = this.segments[segmentOrder];
    if (seg) {
      seg.status = 'done';
      seg.translatedLines = translatedLines;
    }
  }

  onSegError(segmentOrder: number, error: any): void {
    const seg = this.segments[segmentOrder];
    if (seg) {
      seg.status = 'error';
      seg.error = error;
    }
  }

  onAbort(): void {
    for (const seg of this.segments) {
      if (seg.status === 'translating') seg.status = 'pending';
    }
  }

  getSegmentError(segmentOrder: number): string | undefined {
    const err = this.segments[segmentOrder]?.error;
    return err ? err.message ?? String(err) : undefined;
  }

  /** 为已完成的章节注入整章译文 */
  injectDoneTranslation(
    lines: string[],
    translatedLines: string[],
    ranges?: LineRange[],
  ): void {
    this.ranges = ranges ?? [{ start: 0, end: lines.length }];
    this.segments = this.ranges.map((range) => ({
      status: 'done',
      lines: lines.slice(range.start, range.end),
      translatedLines: translatedLines.slice(range.start, range.end),
      error: null,
    }));
    this.ready = true;
    this.textReleased = false;
  }

  /**
   * Drop source/translation text while keeping progress counts. Active
   * segments are left alone because the executor still reads their lines.
   */
  releaseText(): boolean {
    if (this.textReleased) return false;
    if (this.segments.some((segment) => segment.status === 'translating'))
      return false;
    let changed = false;
    for (const segment of this.segments) {
      if (!segment.lines.length && !segment.translatedLines.length) continue;
      segment.lines = [];
      segment.translatedLines = [];
      changed = true;
    }
    this.textReleased = true;
    return changed;
  }
}

export class TaskState {
  readonly taskDesc: string;
  chapters: ChapterMeta[] = [];
  readonly chapterStates = new Map<string, ChapterSegmentState>();
  /** initChapters 执行完毕后设为 true */
  initialized = false;

  constructor(taskDesc: string) {
    this.taskDesc = taskDesc;
  }

  initChapters(chapters: ChapterMeta[]): void {
    this.chapters = chapters;
    this.initialized = true;
  }

  getStatus(chapterId: string): ChapterStatus | undefined {
    return this.chapters.find((c) => c.chapterId === chapterId)?.status;
  }

  updateStatus(chapterId: string, status: ChapterStatus): void {
    const ch = this.chapters.find((c) => c.chapterId === chapterId);
    if (ch) ch.status = status;
  }

  getChapterState(chapterId: string): ChapterSegmentState | undefined {
    if (!this.chapters.some((c) => c.chapterId === chapterId)) return undefined;
    let state = this.chapterStates.get(chapterId);
    if (!state) {
      state = new ChapterSegmentState(chapterId);
      this.chapterStates.set(chapterId, state);
    }
    return state;
  }

  /** Keep preview text for the newest completed chapters only. */
  releaseFinishedChapterTexts(keep = 8): void {
    const completed = [...this.chapterStates.values()].filter(
      (state) => state.allDone && !state.textReleased,
    );
    for (let i = 0; i < completed.length - keep; i++)
      completed[i].releaseText();
  }

  releaseAllChapterTexts(): void {
    for (const state of this.chapterStates.values()) state.releaseText();
  }
}
