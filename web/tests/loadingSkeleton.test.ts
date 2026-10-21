import { describe, expect, it, vi } from 'vitest';
import { createSSRApp, defineComponent, h } from 'vue';
import { renderToString } from 'vue/server-renderer';

import PanelWebNovel from '../src/pages/home/PanelWebNovel.vue';
import PanelWenkuNovel from '../src/pages/home/PanelWenkuNovel.vue';

// 只加载真实异步容器，不引入 web-kit 中无关的全局服务。
vi.mock('@novelia/web-kit', async () => ({
  XAsyncContent: (
    await import(
      '../node_modules/@novelia/web-kit/src/ui/async-content/XAsyncContent.vue'
    )
  ).default,
}));

const Empty = defineComponent({
  props: ['description'],
  setup: (props) => () => h('div', props.description),
});

describe.each([
  ['网络小说', PanelWebNovel],
  ['文库小说', PanelWenkuNovel],
] as const)('%s 首页面板', (_, component) => {
  const render = (novels: [] | undefined, error: Error | null) => {
    const app = createSSRApp({
      render: () => h(component, { novels, error }),
    });
    app.component('NEmpty', Empty);
    app.component(
      'RouterLink',
      defineComponent({
        setup:
          (_, { slots }) =>
          () =>
            h('a', slots.default?.()),
      }),
    );
    return renderToString(app);
  };

  it('只在没有数据且未出错时显示可访问的骨架屏', async () => {
    const loading = await render(undefined, null);
    expect(loading).toContain('role="status"');
    expect(loading).toContain('aria-hidden="true"');
    expect(loading).toContain('skeleton');

    const failed = await render(undefined, new Error('网络异常'));
    expect(failed).toContain('网络异常');
    expect(failed).not.toContain('skeleton');

    const empty = await render([], null);
    expect(empty).toContain('空列表');
    expect(empty).not.toContain('skeleton');
  });

  it('已有数据时优先保留内容，不因错误显示骨架或错误页', async () => {
    const html = await render([], new Error('刷新失败'));
    expect(html).toContain('空列表');
    expect(html).not.toContain('刷新失败');
    expect(html).not.toContain('skeleton');
  });
});
