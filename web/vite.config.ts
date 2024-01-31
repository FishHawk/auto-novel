import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers';
import Components from 'unplugin-vue-components/vite';
import { ProxyOptions, loadEnv } from 'vite';
import topLevelAwait from 'vite-plugin-top-level-await';
import wasm from 'vite-plugin-wasm';
import tsconfigPaths from 'vite-tsconfig-paths';
import { defineConfig } from 'vitest/config';

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
      bypass(req, _res, _options) {
        if (
          req.url &&
          req.url.includes('/translate/') &&
          req.method === 'PUT'
        ) {
          if (req.url.includes('/chapter/')) {
            console.log('检测到网络小说章节翻译请求，已拦截');
            return false;
          }
          if (req.url.includes('/wenku/')) {
            console.log('检测到文库小说章节翻译请求，已拦截');
            return false;
          }
        }
      },
    };

  return {
    server: {
      proxy: {
        '/api': proxyOptions,
      },
    },
    build: {
      cssCodeSplit: false,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('tiktoken')) {
              return 'tiktoken';
            } else if (
              id.includes('data/translator') ||
              id.includes('crypto') ||
              id.includes('uuid') ||
              id.includes('nanoid')
            ) {
              return 'translator';
            } else if (id.includes('web/src') || id.includes('naive')) {
              return 'chunk1';
            } else if (id.includes('node_module')) {
              return 'chunk2';
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
