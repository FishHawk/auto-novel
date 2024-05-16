import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers';
import Components from 'unplugin-vue-components/vite';
import { ProxyOptions, defineConfig, loadEnv } from 'vite';
import { createHtmlPlugin } from 'vite-plugin-html';
import tsconfigPaths from 'vite-tsconfig-paths';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig(({ command, mode }) => {
  const proxy: Record<string, ProxyOptions> = {};

  if (command === 'serve') {
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
    proxy['/api'] = {
      ...proxyOptions,
      bypass(req, _res, _options) {
        if (req.url && req.url.includes('/translate-v2/')) {
          if (req.url.includes('/chapter/')) {
            console.log('检测到小说章节翻译请求，已拦截');
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
      createHtmlPlugin({
        minify: {
          minifyJS: true,
        },
      }),
      tsconfigPaths({ loose: true }),
      AutoImport({
        imports: [
          'vue',
          'vue-router',
          'pinia',
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
      VitePWA({
        registerType: 'autoUpdate',
        pwaAssets: {
          disabled: false,
          image: 'public/icon.svg',
          injectThemeColor: false,
        },
        manifest: {
          name: '轻小说机翻机器人',
          short_name: '轻小说机翻机器人',
          lang: 'zh',
          theme_color: '#ffffff',
        },
        workbox: {
          globPatterns: ['**/*.{js,css,html,png,jpg,svg,webp}'],
        },
      }),
    ],
  };
});
