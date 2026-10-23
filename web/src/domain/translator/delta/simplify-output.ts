import {
  deltaFingerprint,
  type DeltaChapterSession,
  type DeltaReviewState,
} from '@auto-novel/translator';

export type DeltaTextConverter = (text: string) => string | Promise<string>;

async function convertMap(
  values: Record<string, string> | undefined,
  convert: DeltaTextConverter,
): Promise<{ changed: boolean; values: Record<string, string> | undefined }> {
  if (!values) return { changed: false, values };
  let changed = false;
  const next: Record<string, string> = {};
  for (const [key, value] of Object.entries(values)) {
    const converted = await convert(value);
    next[key] = converted;
    if (converted !== value) changed = true;
  }
  return { changed, values: changed ? next : values };
}

async function convertReview(
  review: DeltaReviewState,
  convert: DeltaTextConverter,
): Promise<{ changed: boolean; review: DeltaReviewState }> {
  const translations = await convertMap(review.originalTranslations, convert);
  let changed = translations.changed;

  let changes = review.changes;
  if (changes?.length) {
    const next = await Promise.all(
      changes.map(async (change) => {
        const translation = await convert(change.translation);
        const reason = await convert(change.reason);
        if (translation === change.translation && reason === change.reason)
          return change;
        changed = true;
        return { ...change, translation, reason };
      }),
    );
    if (changed) changes = next;
  }

  let unresolved = review.unresolved;
  if (unresolved?.length) {
    const next = await Promise.all(
      unresolved.map(async (issue) => {
        const reason = await convert(issue.reason);
        if (reason === issue.reason) return issue;
        changed = true;
        return { ...issue, reason };
      }),
    );
    if (changed) unresolved = next;
  }

  return {
    changed,
    review: changed
      ? {
          ...review,
          originalTranslations: translations.values!,
          changes,
          unresolved,
        }
      : review,
  };
}

/**
 * Normalize the finished chapter in place before it is hashed or uploaded.
 * Only user-visible translation state is converted; prompts and request history
 * stay byte-for-byte reproducible so an interrupted Review can still be resumed.
 */
export async function simplifyDeltaOutput(
  session: DeltaChapterSession,
  convert: DeltaTextConverter,
): Promise<Partial<DeltaChapterSession> | undefined> {
  const translations = await convertMap(session.translations, convert);
  let finalParagraphs = session.finalParagraphs;
  if (translations.changed && finalParagraphs) {
    finalParagraphs = await Promise.all(finalParagraphs.map(convert));
  }

  const review = session.review
    ? await convertReview(session.review, convert)
    : undefined;

  if (!translations.changed && !review?.changed) return undefined;

  return {
    ...(translations.changed
      ? {
          translations: translations.values!,
          ...(finalParagraphs
            ? {
                finalParagraphs,
                finalHash: deltaFingerprint(finalParagraphs),
              }
            : {}),
        }
      : {}),
    ...(review?.changed ? { review: review.review } : {}),
  };
}
