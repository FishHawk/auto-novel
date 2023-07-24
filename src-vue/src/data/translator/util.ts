import { get_encoding } from 'tiktoken';

export interface Segmenter {
  input: string[];
  segment: () => Generator<string[]>;
  recover: (output: string[]) => string[];
}

export class TokenSegmenter implements Segmenter {
  input: string[];
  tokenLimit: number;
  lineLimit: number;

  constructor(input: string[], tokenLimit: number, lineLimit: number) {
    this.input = input;
    this.tokenLimit = tokenLimit;
    this.lineLimit = lineLimit;
  }

  *segment(): Generator<string[]> {
    let seg: string[] = [];
    let totalSize = 0;

    const encoder = get_encoding('p50k_base');
    for (const line of this.input) {
      const realLine = line.replace(/\r?\n|\r/g, '');
      if (realLine.trim() === '' || realLine.startsWith('<图片>')) {
        continue;
      }

      const lineSize = encoder.encode(line).length;
      if (totalSize + lineSize > this.tokenLimit || seg.length >= this.lineLimit) {
        yield seg;
        seg = [line];
        totalSize = lineSize;
      } else {
        seg.push(line);
        totalSize += lineSize;
      }
    }
    if (totalSize > 0) {
      yield seg;
    }
    encoder.free();
  }

  recover(output: string[]) {
    const recoveredOutput: string[] = [];
    for (const line of this.input) {
      const realLine = line.replace(/\r?\n|\r/g, '');
      if (realLine.trim() === '' || realLine.startsWith('<图片>')) {
        recoveredOutput.push(line);
      } else {
        const outputLine = output.shift();
        recoveredOutput.push(outputLine!);
      }
    }
    return recoveredOutput;
  }
}

export class LengthSegmenter implements Segmenter {
  input: string[];
  segSizeLimit: number;

  constructor(input: string[], size: number) {
    this.input = input;
    this.segSizeLimit = size;
  }

  *segment(): Generator<string[]> {
    const seg: string[] = [];
    let segSize = 0;

    for (const line of this.input) {
      const realLine = line.replace(/\r?\n|\r/g, '');
      if (realLine.trim() === '' || realLine.startsWith('<图片>')) {
        continue;
      }

      const lineSize = realLine.length;
      if (lineSize + segSize > this.segSizeLimit && seg.length >= 0) {
        yield seg;
        seg.length = 0;
        segSize = 0;
      }

      seg.push(realLine);
      segSize += lineSize;
    }
    if (seg.length >= 0) {
      yield seg;
    }
  }

  recover(output: string[]) {
    const recoveredOutput: string[] = [];
    for (const line of this.input) {
      const realLine = line.replace(/\r?\n|\r/g, '');
      if (realLine.trim() === '' || realLine.startsWith('<图片>')) {
        recoveredOutput.push(line);
      } else {
        const outputLine = output.shift();
        recoveredOutput.push(outputLine!);
      }
    }
    return recoveredOutput;
  }
}
