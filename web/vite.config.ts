import vue from '@vitejs/plugin-vue';
import fs from 'fs';
import path from 'path';
import Sonda from 'sonda/vite';
import AutoImport from 'unplugin-auto-import/vite';
import imagemin from 'unplugin-imagemin/vite';
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers';
import Components from 'unplugin-vue-components/vite';
import { defineConfig, PluginOption, ServerOptions, UserConfig } from 'vite';
import { createHtmlPlugin } from 'vite-plugin-html';
import tsconfigPaths from 'vite-tsconfig-paths';

const enableSonda = process.env.ENABLE_SONDA === '1';
const enableLocalServer = process.env.LOCAL != undefined;

const defineServerOptions = (): ServerOptions => {
  return {
    proxy: {
      '/api': {
        target: enableLocalServer
          ? 'http://localhost:8081'
          : 'https://books.fishhawk.top',
        changeOrigin: true,
        bypass: (req, _res, _options) => {
          if (req.url && req.url.includes('/translate-v2/')) {
            if (req.url.includes('/chapter/')) {
              console.log('检测到小说章节翻译请求，已拦截');
              return false;
            }
          }
        },
        rewrite: (path) => {
          if (enableLocalServer) {
            path = path.replace(/^\/api/, '');
          }
          return path;
        },
      },
      '/files-temp': {
        target: 'https://books.fishhawk.top',
        changeOrigin: true,
      },
    },
  };
};

const filesProxyPlugin = (): PluginOption => ({
  name: 'files-proxy',
  configureServer(server) {
    server.middlewares.use('/files-temp', (req, res) => {
      const url = new URL('http://localhost' + req.url);
      const ext = path.extname(url.pathname).toLowerCase();
      const mimeTypes = {
        '.epub': 'application/epub+zip',
        '.txt': 'text/plain',
      };
      res.setHeader(
        'content-type',
        mimeTypes[ext] || 'application/octet-stream',
      );

      const filePath = path.join(
        __dirname,
        '../server/data/files-temp',
        url.pathname,
      );
      const content = fs.readFileSync(filePath);
      res.end(content);
    });
  },
});

export default defineConfig(({ command, mode }) => {
  const userConfig: UserConfig = {
    build: {
      target: ['es2015', 'edge88', 'firefox78', 'chrome87', 'safari14'],
      cssCodeSplit: false,
      rollupOptions: {
        treeshake: true,
        output: {
          manualChunks(id) {
            if (id.includes('web/src')) {
              return 'chunk';
            } else if (id.includes('@zip.js')) {
              return 'dep-zip';
            } else if (id.includes('opencc')) {
              return 'dep-opencc';
            } else if (id.includes('naive')) {
              return 'dep-naive';
            } else if (id.includes('node_module')) {
              return 'dep';
            }
          },
        },
      },
    },
    plugins: [
      vue(),
      imagemin({}),
      createHtmlPlugin({
        minify: { minifyJS: true },
      }),
      tsconfigPaths({ loose: true }),
      AutoImport({
        dts: true,
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
    ],
  };

  if (command === 'serve') {
    userConfig.server = defineServerOptions();
    if (enableLocalServer) {
      userConfig.plugins.push(filesProxyPlugin());
    }
  }

  if (enableSonda) {
    userConfig.build.sourcemap = true;
    userConfig.plugins.push(
      Sonda({
        gzip: true,
        brotli: true,
      }),
    );
  }

  return userConfig;
});
