import type {
  DeltaBatch,
  DeltaChapterSession,
  DeltaParagraph,
  DeltaSubmitBatch,
} from './types';

export function createDeltaParagraphs(source: string[]): DeltaParagraph[] {
  return source.map((text, index) => ({
    id: `p${String(index + 1).padStart(6, '0')}`,
    source: text,
    indent: text.match(/^[\t \u3000]*/)![0],
    empty: text.trim().length === 0,
  }));
}

/** Fixed bounds keep batching deterministic; an indivisible long paragraph stands alone. */
export function planDeltaBatches(
  paragraphs: DeltaParagraph[],
  maxChars = 1800,
  maxParagraphs = 12,
): DeltaBatch[] {
  const batches: DeltaBatch[] = [];
  let paragraphIds: string[] = [];
  let chars = 0;
  function flush() {
    if (!paragraphIds.length) return;
    batches.push({
      id: `b${String(batches.length + 1).padStart(4, '0')}`,
      paragraphIds,
    });
    paragraphIds = [];
    chars = 0;
  }
  for (const paragraph of paragraphs) {
    if (paragraph.empty) continue;
    if (
      paragraphIds.length &&
      (chars + paragraph.source.length > maxChars ||
        paragraphIds.length >= maxParagraphs)
    )
      flush();
    paragraphIds.push(paragraph.id);
    chars += paragraph.source.length;
  }
  flush();
  return batches;
}

/** Model arguments are the untrusted boundary; no mutation occurs until all entries pass. */
export function validateSubmitBatch(
  session: DeltaChapterSession,
  value: unknown,
): { entries?: DeltaSubmitBatch['entries']; error?: string } {
  if (!value || typeof value !== 'object')
    return { error: '提交内容必须是对象' };
  const args = value as Partial<DeltaSubmitBatch>;
  if (Object.keys(args).some((key) => key !== 'batchId' && key !== 'entries'))
    return { error: '提交内容含有未知参数' };
  const batch = session.batches[session.cursor];
  if (args.batchId !== batch.id) return { error: `batchId 必须是 ${batch.id}` };
  if (!Array.isArray(args.entries)) return { error: 'entries 必须是数组' };
  const authorized = new Set(batch.paragraphIds);
  const seen = new Set<string>();
  const errors: string[] = [];
  for (const entry of args.entries) {
    if (
      !entry ||
      typeof entry.paragraphId !== 'string' ||
      typeof entry.translation !== 'string'
    )
      return { error: '每条译文必须包含 paragraphId 和 translation 字符串' };
    if (
      Object.keys(entry).some(
        (key) => key !== 'paragraphId' && key !== 'translation',
      )
    )
      return { error: '译文条目含有未知参数' };
    if (!authorized.has(entry.paragraphId)) {
      errors.push(
        `段落 ${entry.paragraphId} 不在当前批授权范围内，请删除该条目`,
      );
      continue;
    }
    if (seen.has(entry.paragraphId)) {
      errors.push(`段落 ${entry.paragraphId} 重复，每个 ID 只保留一条译文`);
      continue;
    }
    seen.add(entry.paragraphId);
    if (!entry.translation.trim())
      errors.push(`段落 ${entry.paragraphId} 译文为空，请补充非空译文`);
    if (/[\r\n\u2028\u2029]/.test(entry.translation))
      errors.push(
        `段落 ${entry.paragraphId} 不能包含换行，请将译文写在一个字符串内`,
      );
  }
  const missing = batch.paragraphIds.filter((id) => !seen.has(id));
  if (missing.length) errors.push(`缺失 paragraphId：${missing.join(', ')}`);
  if (errors.length)
    return {
      error: `当前批期望 ${authorized.size} 段，收到 ${args.entries.length} 条条目，覆盖 ${seen.size} 个授权 ID。${errors.join('；')}`,
    };
  return { entries: args.entries };
}

export function assembleDeltaChapter(session: DeltaChapterSession): string[] {
  return session.paragraphs.map((paragraph) => {
    if (paragraph.empty) return paragraph.source;
    const translation = session.translations[paragraph.id];
    if (!translation || /[\r\n\u2028\u2029]/.test(translation))
      throw new Error(`段落 ${paragraph.id} 尚未完成合法翻译`);
    return paragraph.indent + translation.trim();
  });
}
