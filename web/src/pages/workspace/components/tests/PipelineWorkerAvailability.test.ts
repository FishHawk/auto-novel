import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';
import { parse } from 'vue/compiler-sfc';

type Element = {
  type: number;
  tag?: string;
  props?: {
    type: number;
    name: string;
    value?: { content: string };
    arg?: { content: string };
    exp?: { content: string };
  }[];
  children?: Element[];
};

/** Evaluate the actual template bindings under each workspace state. */
function disabledBinding(
  filename: string,
  tag: string,
  attribute = 'disabled',
  action?: string,
) {
  const { descriptor } = parse(
    readFileSync(new URL(`../${filename}`, import.meta.url), 'utf8'),
  );
  const elements: Element[] = [];
  function visit(element: Element) {
    if (element.type === 1) elements.push(element);
    element.children?.forEach(visit);
  }
  visit(descriptor.template!.ast! as unknown as Element);
  const element = elements.find(
    (element) =>
      element.tag === tag &&
      (!action ||
        element.props?.some(
          (prop) =>
            prop.name === 'on' &&
            prop.arg?.content === 'action' &&
            prop.exp?.content.includes(action),
        )),
  );
  const expression = element?.props?.find(
    (prop) => prop.name === 'bind' && prop.arg?.content === attribute,
  )?.exp?.content;
  expect(expression).toBeDefined();
  return (state: Record<string, unknown>) =>
    Boolean(
      new Function(...Object.keys(state), `return (${expression});`)(
        ...Object.values(state),
      ),
    );
}

const defaultDisabled = disabledBinding(
  'PipelineWorkerCard.vue',
  'c-button',
  'disabled',
  "'start'",
);
const deltaDisabled = disabledBinding(
  'PipelineWorkerCard.vue',
  'c-button',
  'disabled',
  "'startDelta'",
);

it('keeps another Delta worker start available while the default pipeline is disabled by a running Delta', () => {
  const state = { startDisabled: true, deltaDisabled: false };
  expect(defaultDisabled(state)).toBe(true);
  expect(deltaDisabled(state)).toBe(false);
});

it('keeps another default worker start available while Delta is disabled by the default pipeline', () => {
  const state = { startDisabled: false, deltaDisabled: true };
  expect(defaultDisabled(state)).toBe(false);
  expect(deltaDisabled(state)).toBe(true);
});

it('blocks both start modes during checkpoint maintenance', () => {
  const state = { startDisabled: true, deltaDisabled: true };
  expect(defaultDisabled(state)).toBe(true);
  expect(deltaDisabled(state)).toBe(true);
});

it('allows unrelated chapter actions during another Delta worker run while respecting the target reservation', () => {
  const workspaceDisabled = disabledBinding(
    '../GptPipelineWorkspace.vue',
    'pipeline-task-card',
    'delta-actions-disabled',
  );
  const previewDisabled = disabledBinding(
    'PipelineTaskCard.vue',
    'delta-preview-modal',
    'actions-disabled',
  );
  const workspace = {
    maintainingCheckpoints: false,
    deltaBusy: true,
    hasRunningWorker: () => false,
  };
  const deltaActionsDisabled = workspaceDisabled(workspace);
  expect(deltaActionsDisabled).toBe(false);
  expect(
    previewDisabled({
      deltaActionsDisabled,
      deltaBusySessionIds: new Set(['busy']),
      activeDeltaSession: { id: 'unrelated' },
    }),
  ).toBe(false);
  expect(
    previewDisabled({
      deltaActionsDisabled,
      deltaBusySessionIds: new Set(['busy']),
      activeDeltaSession: { id: 'busy' },
    }),
  ).toBe(true);
  expect(
    workspaceDisabled({ ...workspace, maintainingCheckpoints: true }),
  ).toBe(true);
  expect(
    workspaceDisabled({ ...workspace, hasRunningWorker: () => true }),
  ).toBe(true);
});
