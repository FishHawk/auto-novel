import { describe, expect, it } from 'vitest';
import { detectChinese } from '../src/utils';

describe('detectChinese', () => {
  it.each([
    'https://example.invalid/path',
    'http://example.invalid/path',
    'ttps://example.invalid/path',
    'ttp://example.invalid/path',
  ])('ignores URL %s when checking the output language', (url) => {
    const text = `这是一段足够长的中文翻译，检查译文语言识别。\n${url}`;

    expect(detectChinese(text)).toBe(true);
  });
});
