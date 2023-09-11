import { ProxyOptions, loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers';
import tsconfigPaths from 'vite-tsconfig-paths';
import wasm from 'vite-plugin-wasm';
import topLevelAwait from 'vite-plugin-top-level-await';

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), 'LOCAL');

  let proxyOptions: ProxyOptions;
  if ('LOCAL' in env)
    proxyOptions = {
      target: 'http://localhost:8081',
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, ''),
    };
  else
    proxyOptions = {
      target: 'https://books.fishhawk.top',
      changeOrigin: true,
    };

  return {
    server: {
      proxy: {
        '/api': proxyOptions,
      },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('tiktoken')) {
              return 'tiktoken';
            } else if (id.includes('crypto')) {
              return 'crypto';
            } else {
              return 'chunk';
            }
          },
        },
      },
    },
    plugins: [
      vue(),
      wasm(),
      topLevelAwait(),
      tsconfigPaths({ loose: true }),
      AutoImport({
        imports: [
          'vue',
          {
            'naive-ui': [
              'useDialog',
              'useMessage',
              'useNotification',
              'useLoadingBar',
            ],
          },
        ],
      }),
      Components({
        resolvers: [NaiveUiResolver()],
        dirs: ['./**/components/**'],
      }),
    ],
  };
});
