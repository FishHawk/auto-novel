import { describe, expect, it } from 'vitest';
import {
  createDeltaSession,
  type DeltaChapterSession,
} from '@auto-novel/translator';
import {
  pruneSessionHistory,
  pruneSessionThinking,
  THINKING_RETENTION_MS,
} from '../thinking-retention';

const now = 1_800_000_000_000;
const old = now - THINKING_RETENTION_MS - 1;
const recent = now - THINKING_RETENTION_MS + 1;

function session(): DeltaChapterSession {
  return {
    messages: [
      {
        role: 'user',
        content: 'prompt',
        timestamp: old,
      },
      {
        role: 'assistant',
        content: [
          { type: 'thinking', thinking: 'old thinking' },
          { type: 'toolCall', id: 'call', name: 'submit_batch', arguments: {} },
        ],
        timestamp: old,
      },
      {
        role: 'assistant',
        content: [{ type: 'thinking', thinking: 'thinking only' }],
        timestamp: old,
      },
      {
        role: 'assistant',
        content: [{ type: 'thinking', thinking: 'recent thinking' }],
        timestamp: recent,
      },
    ],
    review: {
      messages: [
        {
          role: 'assistant',
          content: [{ type: 'thinking', thinking: 'old review thinking' }],
          timestamp: old,
        },
        {
          role: 'assistant',
          content: [{ type: 'thinking', thinking: 'recent review' }],
          timestamp: recent,
        },
      ],
    },
  } as unknown as DeltaChapterSession;
}

describe('thinking retention', () => {
  it('drops thinking older than three days and keeps recent thinking', () => {
    const value = session();
    expect(pruneSessionThinking(value, now)).toBe(true);
    const serialized = JSON.stringify(value.messages);
    expect(serialized).not.toContain('old thinking');
    expect(serialized).not.toContain('thinking only');
    expect(serialized).toContain('recent thinking');
    expect(serialized).toContain('"type":"toolCall"');
    expect(JSON.stringify(value.review!.messages)).not.toContain(
      'old review thinking',
    );
    expect(JSON.stringify(value.review!.messages)).toContain('recent review');
  });

  it('does not rewrite a session without expired thinking', () => {
    const value = session();
    value.messages = value.messages.slice(-1);
    value.review!.messages = value.review!.messages.slice(-1);
    expect(pruneSessionThinking(value, now)).toBe(false);
  });

  it('keeps the exact retention boundary', () => {
    const value = session();
    value.messages = [
      {
        role: 'assistant',
        content: [{ type: 'thinking', thinking: 'boundary' }],
        timestamp: now - THINKING_RETENTION_MS,
      },
    ] as never;
    value.review = undefined;
    expect(pruneSessionThinking(value, now)).toBe(false);
  });
});

function createdSession(
  deltaMode: 'delta' | 'deltax' | 'deltaxs',
  reviewOptions?: { enabled: boolean; mode: 'new' | 'append' },
) {
  return createDeltaSession({
    runId: 'run',
    workerId: 'worker',
    identity: { taskId: 'task', novelId: 'novel', chapterId: 'chapter' },
    source: ['原文一', '原文二'],
    glossary: { 猫: '猫' },
    glossaryId: 'g',
    model: {
      protocol: 'openai-completions',
      endpoint: 'https://test.invalid',
      model: 'model',
    },
    deltaMode,
    reviewOptions,
  });
}

describe('diagnostic retention', () => {
  it('clears replay-only payloads from a completed chapter after the window', () => {
    const value = createdSession('deltax');
    value.phase = 'completed';
    value.completedAt = old;
    value.messages = [
      { role: 'user', content: 'old prompt', timestamp: old },
      {
        role: 'assistant',
        content: [
          { type: 'thinking', thinking: 'old thinking' },
          {
            type: 'toolCall',
            id: 'call',
            name: 'submit_batch',
            arguments: { batchId: 'b0001' },
          },
        ],
        timestamp: old,
      },
    ] as never;
    value.systemPrompt = 'system';
    value.source = ['原文一', '原文二'];
    value.finalParagraphs = ['译文一', '译文二'];
    value.tools = [{ name: 'submit_batch' }] as never;
    value.review = {
      id: 'review',
      options: { enabled: true, mode: 'new' },
      draftHash: 'draft',
      originalTranslations: { p000001: '旧一', p000002: '旧二' },
      systemPrompt: 'review system',
      tools: [],
      messages: [{ role: 'user', content: 'review', timestamp: old }],
      initialMessageCount: 0,
      requests: [],
      attempts: 0,
      failures: 0,
      totalAttempts: 0,
      window: 1,
      contextReads: 0,
      changes: [
        {
          paragraphId: 'p000001',
          translation: '新一',
          category: 'accuracy',
          reason: 'reason',
        },
      ],
      unresolved: [],
      completed: true,
    } as DeltaChapterSession['review'];

    expect(pruneSessionHistory(value, now)).toBe(true);
    expect(value.messages).toEqual([]);
    expect(value.review!.messages).toEqual([]);
    expect(value.source).toEqual([]);
    expect(value.finalParagraphs).toBeUndefined();
    expect(value.systemPrompt).toBe('');
    expect(value.tools).toEqual([]);
    expect(Object.keys(value.review!.originalTranslations)).toEqual([
      'p000001',
    ]);
    expect(value.translations).toEqual({});
  });

  it('keeps recent completed diagnostics untouched', () => {
    const value = createdSession('deltax');
    value.phase = 'completed';
    value.completedAt = recent;
    value.messages = [
      { role: 'user', content: 'recent prompt', timestamp: recent },
    ] as never;
    expect(pruneSessionHistory(value, now)).toBe(false);
    expect(value.messages).toHaveLength(1);
    expect(value.source).toEqual(['原文一', '原文二']);
  });

  it('only trims transcript turns for unfinished fresh-context drafts', () => {
    const value = createdSession('deltax');
    value.messages = [
      { role: 'user', content: 'old prompt', timestamp: old },
      { role: 'user', content: 'recent prompt', timestamp: recent },
    ] as never;
    expect(pruneSessionHistory(value, now)).toBe(true);
    expect(value.messages.map((message) => message.content)).toEqual([
      'recent prompt',
    ]);
  });

  it('keeps native Delta and append-Review transcripts for resume', () => {
    const native = createdSession('delta');
    native.messages = [
      { role: 'user', content: 'old prompt', timestamp: old },
    ] as never;
    expect(pruneSessionHistory(native, now)).toBe(false);
    expect(native.messages).toHaveLength(1);

    const append = createdSession('deltax', {
      enabled: true,
      mode: 'append',
    });
    append.messages = [
      { role: 'user', content: 'old append prompt', timestamp: old },
    ] as never;
    expect(pruneSessionHistory(append, now)).toBe(false);
    expect(append.messages).toHaveLength(1);
  });
});
