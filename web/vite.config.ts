import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers';
import Components from 'unplugin-vue-components/vite';
import { ProxyOptions, defineConfig, loadEnv } from 'vite';
import topLevelAwait from 'vite-plugin-top-level-await';
import wasm from 'vite-plugin-wasm';
import tsconfigPaths from 'vite-tsconfig-paths';

export default defineConfig(({ command, mode }) => {
  const proxy: Record<string, ProxyOptions> = {};

  if (command === 'serve') {
    const env = loadEnv(mode, process.cwd(), 'LOCAL');
    if ('LOCAL' in env)
      proxy['/api'] = {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      };
    else
      proxy['/api'] = {
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
  }
  return {
    server: {
      proxy,
    },
    build: {
      cssCodeSplit: false,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('web/src')) {
              return 'chunk';
            } else if (id.includes('@zip.js')) {
              return 'zip';
            } else if (id.includes('naive')) {
              return 'naive';
            } else if (id.includes('node_module')) {
              return 'dep';
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
          'vue-router',
          {
            'naive-ui': [
              'useDialog',
              'useMessage',
              'useNotification',
              'useLoadingBar',
              'useThemeVars',
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
