import { createMarkdown } from '@novelia/web-kit';
import anchor from 'markdown-it-anchor';

export type MarkdownMode = 'article' | 'comment';

const article = createMarkdown().use(anchor, {
  // 保持中文片段链接与已有文章锚点兼容。
  slugify: (title: string) => title.trim().toLowerCase().replace(/\s+/g, '-'),
});
const comment = createMarkdown().disable([
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

export const markdownByMode = { article, comment };
