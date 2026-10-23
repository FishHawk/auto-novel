import { describe, expect, it } from 'vitest';
import {
  deltaFingerprint,
  type DeltaChapterSession,
} from '@auto-novel/translator';
import { simplifyDeltaOutput } from '../simplify-output';

const convert = (text: string) =>
  text.replaceAll('繁', '简').replaceAll('體', '体');

function fixture(): DeltaChapterSession {
  return {
    translations: { p1: '繁體', p2: '已经是简体' },
    finalParagraphs: ['　繁體', '已经是简体'],
    finalHash: 'old-hash',
    review: {
      originalTranslations: { p1: '繁體', p2: '已经是简体' },
      changes: [
        {
          paragraphId: 'p1',
          translation: '繁體',
          category: 'fluency',
          reason: '繁體用词',
        },
      ],
      unresolved: [{ paragraphId: 'p1', reason: '繁體待确认' }],
    },
  } as unknown as DeltaChapterSession;
}

describe('Delta output simplification', () => {
  it('converts stored translations, final paragraphs and review details', async () => {
    const session = fixture();
    const patch = await simplifyDeltaOutput(session, convert);

    expect(patch?.translations).toEqual({
      p1: '简体',
      p2: '已经是简体',
    });
    expect(patch?.finalParagraphs).toEqual(['　简体', '已经是简体']);
    expect(patch?.finalHash).toBe(deltaFingerprint(['　简体', '已经是简体']));
    expect(patch?.review?.originalTranslations).toEqual({
      p1: '简体',
      p2: '已经是简体',
    });
    expect(patch?.review?.changes).toEqual([
      {
        paragraphId: 'p1',
        translation: '简体',
        category: 'fluency',
        reason: '简体用词',
      },
    ]);
    expect(patch?.review?.unresolved).toEqual([
      { paragraphId: 'p1', reason: '简体待确认' },
    ]);
  });

  it('does not persist an identical session', async () => {
    const session = {
      translations: { p1: '已经是简体' },
      finalParagraphs: ['已经是简体'],
      finalHash: 'same',
    } as unknown as DeltaChapterSession;

    await expect(
      simplifyDeltaOutput(session, convert),
    ).resolves.toBeUndefined();
  });

  it('keeps indentation outside the converted text untouched', async () => {
    const session = {
      translations: { p1: '繁體' },
      finalParagraphs: ['\u3000\u3000繁體'],
      finalHash: 'old',
    } as unknown as DeltaChapterSession;

    const patch = await simplifyDeltaOutput(session, convert);
    expect(patch?.finalParagraphs).toEqual(['\u3000\u3000简体']);
  });
});
