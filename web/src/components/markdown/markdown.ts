import { createMarkdown } from '@novelia/web-kit';

export const markdown = createMarkdown().disable([
  'blockquote',
  'code',
  'fence',
  'heading',
  'hr',
  'image',
  'lheading',
  'reference',
  'table',
]);
