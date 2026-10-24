import type { HarnessOutputFormat } from './presets';

/**
 * 模型输出格式约定与解析。
 *
 * 不同模型对结构化输出的支持程度不一样，所以预设里可选三种格式：
 * - json：返回 {"translations": [...]}，适合支持 JSON mode 的接口；
 * - paragraph-id：逐行返回“段落ID#译文”，最容易发现漏段、串段；
 * - lines：一行一段，最省 token，但最依赖模型遵循指令的能力。
 *
 * 三种格式解析后都会归一成 string[]
 */
export const outputInstructions: Record<HarnessOutputFormat, string> = {
  json: '按本批顺序返回 JSON，例如 {"translations":["第一段译文","第二段译文"]}，数组中的译文不包含段落 ID。',
  'paragraph-id':
    '按本批顺序逐行返回“段落ID#译文”，使用 current_batch 中的对应 ID，例如 p0000#第一段译文。不输出 JSON、Markdown 或任何解释。',
  lines: '按本批顺序每行输出一段译文，不添加段落 ID、JSON、Markdown 或解释。',
};

export function parseBatchOutput(
  text: string,
  format: HarnessOutputFormat,
  paragraphIds: string[],
): string[] {
  let translations: unknown;
  switch (format) {
    case 'json':
      translations = JSON.parse(text)?.translations;
      break;
    case 'paragraph-id':
      // 逐行校验模型是否按“本批段落 ID 顺序”返回：
      // 只要有一行 ID 对不上，就说明模型漏段、串段或改了顺序，
      // 此时宁可整章失败，也不能把错位译文写进文库。
      translations = text
        .trim()
        .split(/\r?\n/)
        .map((line, index) => {
          const prefix = `${paragraphIds[index]}#`;
          if (!line.startsWith(prefix)) {
            throw new Error(`段落 ID 不匹配，需要 ${paragraphIds[index]}`);
          }
          return line.slice(prefix.length);
        });
      break;
    case 'lines':
      translations = text.trim().split(/\r?\n/);
      break;
  }
  // 统一校验：必须是数组、段数与本批一致，且每段都是非空单行文本。
  // 模型偶尔会返回 Markdown 列表或多行解释，这里直接拒绝而不是猜测修复。
  if (
    !Array.isArray(translations) ||
    translations.length !== paragraphIds.length ||
    !translations.every(
      (line: unknown) =>
        typeof line === 'string' &&
        line.trim().length > 0 &&
        !/[\r\n]/.test(line),
    )
  ) {
    throw new Error(`译文格式错误，需要 ${paragraphIds.length} 段单行译文`);
  }
  return translations;
}
