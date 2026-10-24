import { translateChapter } from '@auto-novel/translator/harness';
import type { HarnessPreset } from '@auto-novel/translator/harness/presets';
import type { ChapterTranslator } from '../TaskExecutor';

/**
 * 把所选模型预设包装成 TaskExecutor 可直接调用的“章节翻译函数”。
 *
 * 这里不经过 TranslationPipeline：章节并发由 Workspace 分配槽位控制，
 * 进度展示则通过 tracker.segmentTracker 回填，保持和 pipeline 模式一致的界面表现。
 */
export function createHarnessMode(
  preset: HarnessPreset,
  key: string,
): ChapterTranslator {
  return async (detail, tracker, signal) => {
    const translated = await translateChapter({
      source: detail.paragraphs,
      glossary: detail.glossary,
      preset,
      key,
      signal,
      onLog: tracker.onLog,
      onUsage: (record) => {
        tracker.segmentTracker?.harnessUsage.push(record);
      },
    });
    // harness 内部按批翻译，没有 pipeline 的分段回调；
    // 整章完成后一次性把段落状态标为 done，让进度条和分段视图仍能正确显示。
    tracker.segmentTracker?.injectDoneTranslation(
      detail.paragraphs,
      translated,
    );
    return translated;
  };
}
