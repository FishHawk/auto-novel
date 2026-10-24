/**
 * 「Harness」翻译器：不经过 TranslationPipeline 的分块调度，直接把整章按固定
 * 批大小发给模型，由模型返回整段译文。
 *
 * 与 pipeline 模式的区别：
 * - pipeline 把章节切成不定长分块，交给多个翻译器并行领取；
 * - harness 以章节为单位、按批串行请求，适合需要完整上下文的预设。
 *
 * 为了保证模型输出能和原文逐段对齐，每段原文都会带一个稳定编号
 * （p0000、p0001…）。模型每次只翻译当前批次，但仍能看到整章原文、
 * 术语表和已经翻好的部分，从而保持称谓、语气和上下文一致。
 */
import type { Glossary, Logger } from '../types';
import { createPiAiCompletion } from '../translator/pi-ai-api';
import type { HarnessPreset } from './presets';
import { outputInstructions, parseBatchOutput } from './output';

export interface HarnessInput {
  source: string[];
  glossary: Glossary;
  preset: HarnessPreset;
  key: string;
  signal?: AbortSignal;
  onLog?: Logger;
}

/**
 * 单个批次包含的原文段数。批次太大容易超出模型上下文，太小则会丢失
 * 段落之间的语境，还会增加请求次数。
 */
const BATCH_SIZE = 20;
/** 段落编号固定 4 位，保证按字符串排序时顺序仍与原文一致。 */
const paragraphId = (index: number) => `p${String(index).padStart(4, '0')}`;

/**
 * 把带编号的段落拼成多行文本，供提示词使用。
 * 空行不发送，也不会占用编号；start 用于让批次内的编号延续整章序号。
 */
const numberedLines = (lines: string[], start = 0) =>
  lines
    .map((line, index) =>
      line.trim() ? `${paragraphId(start + index)}#${line}` : '',
    )
    .filter(Boolean)
    .join('\n');

export async function translateChapter({
  source,
  glossary,
  preset,
  key,
  signal,
  onLog,
}: HarnessInput): Promise<string[]> {
  const translated: string[] = [];
  // 每次请求都会重复带上整章原文和术语表：模型看到当前批次之外的内容后，
  // 更容易保持人物称谓、语气和专有名词的一致。这部分只作为输入，不产生译文输出。
  const prefix = [
    `<chapter_source>\n${numberedLines(source)}\n</chapter_source>`,
    `<glossary>\n${Object.entries(glossary)
      .map(([jp, zh]) => `${jp} => ${zh}`)
      .join('\n')}\n</glossary>`,
  ].join('\n\n');

  // 批与批之间串行：下一批的提示词里要带上前面批次的译文，
  for (let start = 0; start < source.length; start += BATCH_SIZE) {
    signal?.throwIfAborted();
    const lines = source.slice(start, start + BATCH_SIZE);
    // 只把非空段落计入本批：空行原样保留，不需要也不应该让模型翻译。
    const paragraphIds = lines.flatMap((line, index) =>
      line.trim() ? [paragraphId(start + index)] : [],
    );
    const count = paragraphIds.length;
    let translations: string[] = [];
    if (count > 0) {
      onLog?.(
        `翻译批次 ${Math.floor(start / BATCH_SIZE) + 1}/${Math.ceil(source.length / BATCH_SIZE)}`,
      );
      // 请求结构：system 放翻译规则和输出格式；user 按
      // 「整章原文 → 已译内容 → 当前批次」摆放，最后明确要求返回本批译文。
      // 把 current_batch 放在最后，模型更容易把注意力落在真正要翻译的段落上。
      const response = await createPiAiCompletion({
        protocol: preset.protocol,
        endpoint: preset.endpoint,
        model: preset.model,
        key,
        signal,
        messages: [
          {
            role: 'system',
            content: `${preset.rules}\n全章原文和已译部分供理解上下文，只翻译 current_batch。
${outputInstructions[preset.outputFormat]}
每段对应一行非空译文，不要合并或拆分段落。`,
          },
          {
            role: 'user',
            content: [
              prefix,
              `<translated_so_far>\n${numberedLines(translated)}\n</translated_so_far>`,
              `<current_batch>\n${numberedLines(lines, start)}\n</current_batch>`,
              `请返回本批 ${count} 段的译文。`,
            ].join('\n\n'),
          },
        ],
        requestParams: preset.requestParams,
      });
      signal?.throwIfAborted();
      // 解析和数量校验统一放在 output.ts；格式不符会直接抛错，
      // 由上层把整章标记为失败，避免错位的译文被上传。
      translations = parseBatchOutput(
        response.text,
        preset.outputFormat,
        paragraphIds,
      );
    }
    // 把本批译文逐段对回原文位置：
    // - 非空段落沿用原文行首缩进，避免模型改动排版；
    // - 空行原样保留（包括空字符串），保证返回数组长度和 source 完全一致。
    let index = 0;
    translated.push(
      ...lines.map((line) =>
        line.trim()
          ? line.match(/^\s*/)![0] + translations[index++].trim()
          : line,
      ),
    );
  }
  return translated;
}
