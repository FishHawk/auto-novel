import { Segmentor } from '../type';

export const createTokenSegmenter =
  (maxToken: number, maxLine: number): Segmentor =>
  async (input: string[]) => {
    const Tiktoken = (await import('tiktoken/lite')).Tiktoken;
    const p50k_base = (await import('tiktoken/encoders/p50k_base')).default;
    const encoder = new Tiktoken(
      p50k_base.bpe_ranks,
      p50k_base.special_tokens,
      p50k_base.pat_str
    );

    const segs: string[][] = [];
    let seg: string[] = [];
    let segSize = 0;

    for (const line of input) {
      const lineSize = encoder.encode(line).length;
      if (
        (segSize + lineSize > maxToken || seg.length >= maxLine) &&
        seg.length > 0
      ) {
        segs.push(seg);
        seg = [line];
        segSize = lineSize;
      } else {
        seg.push(line);
        segSize += lineSize;
      }
    }

    if (seg.length > 0) {
      segs.push(seg);
    }

    // 如果最后的分段过小，与上一个分段合并。
    if (segs.length >= 2) {
      const last1Seg = segs[segs.length - 1];
      const last1TokenSize = last1Seg.reduce(
        (a, b) => a + encoder.encode(b).length,
        0
      );
      if (last1Seg.length <= 5 && last1TokenSize <= 500) {
        const last2Seg = segs[segs.length - 2];
        last2Seg.push(...last1Seg);
        segs.pop();
      }
    }

    encoder.free();

    return segs;
  };

export function* parseEventStream<T>(text: string) {
  for (const line of text.split('\n')) {
    if (line == '[DONE]') {
      return;
    } else if (!line.trim()) {
      continue;
    } else {
      try {
        const obj: T = JSON.parse(line.replace(/^data\:/, '').trim());
        yield obj;
      } catch {
        continue;
      }
    }
  }
}
