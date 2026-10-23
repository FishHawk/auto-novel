import type { AssistantMessage } from '@earendil-works/pi-ai';

/** 文本协议：模型不经过工具调用，直接以纯文本回复。 */
export function assistantText(message: AssistantMessage): string {
  return message.content
    .filter((block) => block.type === 'text')
    .map((block) => block.text)
    .join('')
    .trim();
}

/** 解析逐行“paragraphId<TAB>译文”文本；其余行自动忽略，未识别到译文时返回 undefined。 */
export function parseBatchText(text: string, batchId: string): unknown {
  const entries = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const match =
        line.match(/^(p\d{6})(?:<TAB>|\t|[:：>]\s*|\s+)(.+)$/) ??
        line.match(/^(p\d{6})(.+)$/);
      return match
        ? { paragraphId: match[1], translation: match[2].trim() }
        : undefined;
    })
    .filter(Boolean);
  return entries.length ? { batchId, entries } : undefined;
}

const KANA_RE = /[\u3041-\u3096\u30a1-\u30f6]/;

// 未翻译判定阈值（对 800+ 段真实译文的离线分布取中间值：正常译文相似度 ≤0.4、
// 假名占比 ≤0.07；日文回显/日语改写段相似度 ≥0.33 或假名占比 ≥0.47，两条件取“或”）。
const UNTRANSLATED_SIMILARITY = 0.55;
const UNTRANSLATED_KANA_RATIO = 0.25;

function normalizedChars(text: string): string {
  return text
    .replace(/[\s\u3000]/g, '')
    .replace(/[「」『』“”"']/g, '')
    .replace(/[、。！？!?…—]/g, '');
}

function charNgrams(chars: string, size = 3): Map<string, number> {
  const map = new Map<string, number>();
  for (let i = 0; i + size <= chars.length; i++) {
    const key = chars.slice(i, i + size);
    map.set(key, (map.get(key) ?? 0) + 1);
  }
  return map;
}

function ngramF1(left: string, right: string, size = 3): number {
  const a = charNgrams(left, size);
  const b = charNgrams(right, size);
  let aTotal = 0;
  let bTotal = 0;
  let overlap = 0;
  for (const count of a.values()) aTotal += count;
  for (const count of b.values()) bTotal += count;
  for (const [key, count] of a) overlap += Math.min(count, b.get(key) ?? 0);
  if (!aTotal || !bTotal) return 0;
  const precision = overlap / aTotal;
  const recall = overlap / bTotal;
  return precision + recall === 0
    ? 0
    : (2 * precision * recall) / (precision + recall);
}

/**
 * 译文仍是日文（原文回显或日语语内改写）而非中文翻译时返回 true。
 * 源文不含假名（纯符号、缩写等）时一律放行；少量假名残留（网名等）不触发。
 */
export function looksUntranslated(
  source: string,
  translation: string,
): boolean {
  if (!KANA_RE.test(source)) return false;
  const chars = normalizedChars(translation);
  if (!chars) return false;
  const kana = [...chars].filter((char) => KANA_RE.test(char)).length;
  if (kana / chars.length >= UNTRANSLATED_KANA_RATIO) return true;
  return ngramF1(chars, normalizedChars(source)) >= UNTRANSLATED_SIMILARITY;
}
