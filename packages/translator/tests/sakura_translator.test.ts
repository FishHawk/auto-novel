import { afterEach, describe, expect, it, vi } from 'vitest';

import { SakuraTranslator } from '@/translator/sakura-translator';

const json = (body: unknown) =>
  new Response(JSON.stringify(body), {
    headers: { 'content-type': 'application/json' },
  });

describe('SakuraTranslator line-by-line fallback', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('keeps one translated line per source line', async () => {
    let chatCalls = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: Request | string) => {
        const url = typeof input === 'string' ? input : input.url;
        if (url.endsWith('/models')) return json({ data: [] });
        chatCalls++;
        // 前 3 次整段翻译行数不匹配，触发逐行翻译；逐行时模型输出带换行
        const content = chatCalls <= 3 ? '只有一行' : '甲\n乙';
        return json({
          choices: [{ message: { content } }],
          usage: { completion_tokens: 1 },
        });
      }),
    );

    const translator = await SakuraTranslator.create({
      endpoint: 'https://sakura.test',
    });
    const result = await translator.translate(['あ', '', 'い']);

    expect(result).toEqual(['甲乙', '', '甲乙']);
    expect(chatCalls).toBe(3 + 2); // 空行不请求模型
  });
});

describe('SakuraTranslator blank lines', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('sends only content lines and restores blank and image lines', async () => {
    const prompts: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: Request | string) => {
        const request = typeof input === 'string' ? new Request(input) : input;
        if (request.url.endsWith('/models')) return json({ data: [] });
        const body = await request.json();
        prompts.push(body.messages.at(-1).content);
        return json({
          choices: [{ message: { content: '甲\n乙' } }],
          usage: { completion_tokens: 1 },
        });
      }),
    );

    const translator = await SakuraTranslator.create({
      endpoint: 'https://sakura.test',
    });
    const result = await translator.translate(['あ', '', '<图片>x', '', 'い']);

    expect(result).toEqual(['甲', '', '<图片>x', '', '乙']);
    expect(prompts).toHaveLength(1);
    expect(prompts[0]).toContain('あ\nい');
  });
});
