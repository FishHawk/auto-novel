import { readFileSync } from 'node:fs';
import { compileScript, parse } from 'vue/compiler-sfc';
import * as vue from 'vue';
import ts from 'typescript';

/** Run the real setup script without introducing a browser or component stubs. */
export function setupSfc<T>(
  filename: string,
  props: Record<string, unknown>,
  imports: Record<string, unknown> = {},
  injectedGlobals: Record<string, unknown> = {},
) {
  const source = readFileSync(
    new URL(`../${filename}`, import.meta.url),
    'utf8',
  );
  const { descriptor } = parse(source, { filename });
  const script = compileScript(descriptor, { id: filename });
  const { outputText } = ts.transpileModule(script.content, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  });
  const exports: { default?: vue.Component } = {};
  const globals = {
    ref: vue.ref,
    computed: vue.computed,
    reactive: vue.reactive,
    watch: vue.watch,
    nextTick: vue.nextTick,
    useTemplateRef: vue.useTemplateRef,
    ...injectedGlobals,
  };
  new Function('require', 'exports', ...Object.keys(globals), outputText)(
    (id: string) => {
      if (id === 'vue') return vue;
      if (id in imports) return imports[id];
      throw new Error(`Unexpected runtime import: ${id}`);
    },
    exports,
    ...Object.values(globals),
  );
  const renderer = vue.createRenderer<Record<string, unknown>, object>({
    createElement: () => ({}),
    createText: () => ({}),
    createComment: () => ({}),
    insert: () => {},
    remove: () => {},
    setText: () => {},
    setElementText: () => {},
    parentNode: () => null,
    nextSibling: () => null,
    patchProp: () => {},
  });
  const app = renderer.createApp(
    { ...exports.default, render: () => null },
    props,
  );
  app.mount({});
  const instance = app._instance! as vue.ComponentInternalInstance & {
    setupState: T;
  };
  return {
    state: instance.setupState,
    form: instance.refs as Record<string, unknown>,
    unmount: () => app.unmount(),
  };
}
