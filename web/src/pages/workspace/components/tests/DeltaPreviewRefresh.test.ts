import { readFileSync } from 'node:fs';
import { afterEach, expect, it } from 'vitest';
import { parse } from 'vue/compiler-sfc';
import { isBlockingDeltaReviewIssue } from '@auto-novel/translator';
import { setupSfc } from './sfc';

type TemplateElement = {
  type: number;
  tag?: string;
  props?: {
    type: number;
    name: string;
    value?: { content: string };
    arg?: { content: string };
    exp?: { content: string };
  }[];
  children?: TemplateElement[];
};
function templateElements(filename: string) {
  const { descriptor } = parse(
    readFileSync(new URL(`../${filename}`, import.meta.url), 'utf8'),
  );
  const elements: TemplateElement[] = [];
  function visit(node: TemplateElement) {
    if (node.type === 1) elements.push(node);
    node.children?.forEach(visit);
  }
  visit(descriptor.template!.ast! as unknown as TemplateElement);
  return elements;
}
function binding(node: TemplateElement, name: string, argument?: string) {
  return node.props?.find(
    (prop) =>
      prop.type === 7 && prop.name === name && prop.arg?.content === argument,
  )?.exp?.content;
}
const cleanups: (() => void)[] = [];
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

it('shows an actionable refresh warning inside the preview while retaining its chapter', () => {
  const events: string[] = [];
  const session = { id: 'checkpoint', revision: 1 };
  const setup = setupSfc<{
    detailRefreshWarning: string | null;
    retryDetail: () => void;
  }>(
    'DeltaPreviewModal.vue',
    {
      show: true,
      title: 'Chapter',
      session,
      detailError: 'Checkpoint read failed',
      detailLoading: false,
      onRetryDetail: () => events.push('retry'),
    },
    {
      '@auto-novel/translator': { isBlockingDeltaReviewIssue },
      '@novelia/web-kit': { XDialog: {} },
    },
    { useThemeVars: () => ({}) },
  );
  cleanups.push(setup.unmount);
  expect(setup.state.detailRefreshWarning).toContain('Checkpoint read failed');
  const warning = templateElements('DeltaPreviewModal.vue').find(
    (node) =>
      node.tag === 'n-alert' && binding(node, 'if') === 'detailRefreshWarning',
  );
  expect(warning).toBeDefined();
  const button = warning?.children
    ?.flatMap((child) => [child, ...(child.children ?? [])])
    .find((node) => node.tag === 'c-button');
  expect(button).toBeDefined();
  expect(binding(button!, 'on', 'action')).toBe('retryDetail');
  expect(binding(button!, 'bind', 'disabled')).toBe('detailLoading');
  setup.state.retryDetail();
  expect(events).toEqual(['retry']);
});

it('forwards refresh failures and retry actions through the existing preview component', () => {
  const preview = templateElements('PipelineTaskCard.vue').find(
    (node) => node.tag === 'delta-preview-modal',
  );
  expect(preview).toBeDefined();
  expect(binding(preview!, 'bind', 'detail-error')).toBe('deltaDetailError');
  expect(binding(preview!, 'bind', 'detail-loading')).toBe(
    'deltaDetailLoading',
  );
  expect(binding(preview!, 'on', 'retry-detail')).toBe('retryDeltaDetail');
});

it('unmounts the complete preview on close to release derived history caches', () => {
  const preview = templateElements('PipelineTaskCard.vue').find(
    (node) => node.tag === 'delta-preview-modal',
  );
  expect(binding(preview!, 'if')).toBe(
    'showDeltaPreview && activeDeltaSession',
  );
});
