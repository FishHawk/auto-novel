// vite.config.ts
import vue from "file:///home/wh/Projects/auto-novel/web/node_modules/.pnpm/@vitejs+plugin-vue@5.0.3_vite@5.0.11_vue@3.4.14/node_modules/@vitejs/plugin-vue/dist/index.mjs";
import AutoImport from "file:///home/wh/Projects/auto-novel/web/node_modules/.pnpm/unplugin-auto-import@0.16.7_@vueuse+core@10.7.2/node_modules/unplugin-auto-import/dist/vite.js";
import { NaiveUiResolver } from "file:///home/wh/Projects/auto-novel/web/node_modules/.pnpm/unplugin-vue-components@0.25.2_vue@3.4.14/node_modules/unplugin-vue-components/dist/resolvers.mjs";
import Components from "file:///home/wh/Projects/auto-novel/web/node_modules/.pnpm/unplugin-vue-components@0.25.2_vue@3.4.14/node_modules/unplugin-vue-components/dist/vite.mjs";
import { loadEnv } from "file:///home/wh/Projects/auto-novel/web/node_modules/.pnpm/vite@5.0.11/node_modules/vite/dist/node/index.js";
import topLevelAwait from "file:///home/wh/Projects/auto-novel/web/node_modules/.pnpm/vite-plugin-top-level-await@1.4.1_vite@5.0.11/node_modules/vite-plugin-top-level-await/exports/import.mjs";
import wasm from "file:///home/wh/Projects/auto-novel/web/node_modules/.pnpm/vite-plugin-wasm@3.3.0_vite@5.0.11/node_modules/vite-plugin-wasm/exports/import.mjs";
import tsconfigPaths from "file:///home/wh/Projects/auto-novel/web/node_modules/.pnpm/vite-tsconfig-paths@4.3.0_typescript@5.3.3_vite@5.0.11/node_modules/vite-tsconfig-paths/dist/index.mjs";
import { defineConfig } from "file:///home/wh/Projects/auto-novel/web/node_modules/.pnpm/vitest@1.2.0/node_modules/vitest/dist/config.js";
var vite_config_default = defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), "LOCAL");
  let proxyOptions;
  if ("LOCAL" in env)
    proxyOptions = {
      target: "http://localhost:8081",
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, "")
    };
  else
    proxyOptions = {
      target: "https://books1.fishhawk.top",
      changeOrigin: true,
      bypass(req, _res, _options) {
        if (req.url && req.url.includes("/translate/") && req.method === "PUT") {
          if (req.url.includes("/chapter/")) {
            console.log("\u68C0\u6D4B\u5230\u7F51\u7EDC\u5C0F\u8BF4\u7AE0\u8282\u7FFB\u8BD1\u8BF7\u6C42\uFF0C\u5DF2\u62E6\u622A");
            return false;
          }
          if (req.url.includes("/wenku/")) {
            console.log("\u68C0\u6D4B\u5230\u6587\u5E93\u5C0F\u8BF4\u7AE0\u8282\u7FFB\u8BD1\u8BF7\u6C42\uFF0C\u5DF2\u62E6\u622A");
            return false;
          }
        }
      }
    };
  return {
    server: {
      proxy: {
        "/api": proxyOptions
      }
    },
    build: {
      cssCodeSplit: false,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes("tiktoken")) {
              return "tiktoken";
            } else if (id.includes("web/src")) {
              return "chunk";
            } else if (id.includes("naive")) {
              return "naive";
            } else if (id.includes("node_module")) {
              return "dep";
            }
          }
        }
      }
    },
    plugins: [
      vue(),
      wasm(),
      topLevelAwait(),
      tsconfigPaths({ loose: true }),
      AutoImport({
        imports: [
          "vue",
          {
            "naive-ui": [
              "useDialog",
              "useMessage",
              "useNotification",
              "useLoadingBar"
            ]
          }
        ]
      }),
      Components({
        resolvers: [NaiveUiResolver()],
        dirs: ["./**/components/**"]
      })
    ]
  };
});
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcudHMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCIvaG9tZS93aC9Qcm9qZWN0cy9hdXRvLW5vdmVsL3dlYlwiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9maWxlbmFtZSA9IFwiL2hvbWUvd2gvUHJvamVjdHMvYXV0by1ub3ZlbC93ZWIvdml0ZS5jb25maWcudHNcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfaW1wb3J0X21ldGFfdXJsID0gXCJmaWxlOi8vL2hvbWUvd2gvUHJvamVjdHMvYXV0by1ub3ZlbC93ZWIvdml0ZS5jb25maWcudHNcIjtpbXBvcnQgdnVlIGZyb20gJ0B2aXRlanMvcGx1Z2luLXZ1ZSc7XG5pbXBvcnQgQXV0b0ltcG9ydCBmcm9tICd1bnBsdWdpbi1hdXRvLWltcG9ydC92aXRlJztcbmltcG9ydCB7IE5haXZlVWlSZXNvbHZlciB9IGZyb20gJ3VucGx1Z2luLXZ1ZS1jb21wb25lbnRzL3Jlc29sdmVycyc7XG5pbXBvcnQgQ29tcG9uZW50cyBmcm9tICd1bnBsdWdpbi12dWUtY29tcG9uZW50cy92aXRlJztcbmltcG9ydCB7IFByb3h5T3B0aW9ucywgbG9hZEVudiB9IGZyb20gJ3ZpdGUnO1xuaW1wb3J0IHRvcExldmVsQXdhaXQgZnJvbSAndml0ZS1wbHVnaW4tdG9wLWxldmVsLWF3YWl0JztcbmltcG9ydCB3YXNtIGZyb20gJ3ZpdGUtcGx1Z2luLXdhc20nO1xuaW1wb3J0IHRzY29uZmlnUGF0aHMgZnJvbSAndml0ZS10c2NvbmZpZy1wYXRocyc7XG5pbXBvcnQgeyBkZWZpbmVDb25maWcgfSBmcm9tICd2aXRlc3QvY29uZmlnJztcblxuZXhwb3J0IGRlZmF1bHQgZGVmaW5lQ29uZmlnKCh7IGNvbW1hbmQsIG1vZGUgfSkgPT4ge1xuICBjb25zdCBlbnYgPSBsb2FkRW52KG1vZGUsIHByb2Nlc3MuY3dkKCksICdMT0NBTCcpO1xuXG4gIGxldCBwcm94eU9wdGlvbnM6IFByb3h5T3B0aW9ucztcbiAgaWYgKCdMT0NBTCcgaW4gZW52KVxuICAgIHByb3h5T3B0aW9ucyA9IHtcbiAgICAgIHRhcmdldDogJ2h0dHA6Ly9sb2NhbGhvc3Q6ODA4MScsXG4gICAgICBjaGFuZ2VPcmlnaW46IHRydWUsXG4gICAgICByZXdyaXRlOiAocGF0aCkgPT4gcGF0aC5yZXBsYWNlKC9eXFwvYXBpLywgJycpLFxuICAgIH07XG4gIGVsc2VcbiAgICBwcm94eU9wdGlvbnMgPSB7XG4gICAgICB0YXJnZXQ6ICdodHRwczovL2Jvb2tzMS5maXNoaGF3ay50b3AnLFxuICAgICAgY2hhbmdlT3JpZ2luOiB0cnVlLFxuICAgICAgYnlwYXNzKHJlcSwgX3JlcywgX29wdGlvbnMpIHtcbiAgICAgICAgaWYgKFxuICAgICAgICAgIHJlcS51cmwgJiZcbiAgICAgICAgICByZXEudXJsLmluY2x1ZGVzKCcvdHJhbnNsYXRlLycpICYmXG4gICAgICAgICAgcmVxLm1ldGhvZCA9PT0gJ1BVVCdcbiAgICAgICAgKSB7XG4gICAgICAgICAgaWYgKHJlcS51cmwuaW5jbHVkZXMoJy9jaGFwdGVyLycpKSB7XG4gICAgICAgICAgICBjb25zb2xlLmxvZygnXHU2OEMwXHU2RDRCXHU1MjMwXHU3RjUxXHU3RURDXHU1QzBGXHU4QkY0XHU3QUUwXHU4MjgyXHU3RkZCXHU4QkQxXHU4QkY3XHU2QzQyXHVGRjBDXHU1REYyXHU2MkU2XHU2MjJBJyk7XG4gICAgICAgICAgICByZXR1cm4gZmFsc2U7XG4gICAgICAgICAgfVxuICAgICAgICAgIGlmIChyZXEudXJsLmluY2x1ZGVzKCcvd2Vua3UvJykpIHtcbiAgICAgICAgICAgIGNvbnNvbGUubG9nKCdcdTY4QzBcdTZENEJcdTUyMzBcdTY1ODdcdTVFOTNcdTVDMEZcdThCRjRcdTdBRTBcdTgyODJcdTdGRkJcdThCRDFcdThCRjdcdTZDNDJcdUZGMENcdTVERjJcdTYyRTZcdTYyMkEnKTtcbiAgICAgICAgICAgIHJldHVybiBmYWxzZTtcbiAgICAgICAgICB9XG4gICAgICAgIH1cbiAgICAgIH0sXG4gICAgfTtcblxuICByZXR1cm4ge1xuICAgIHNlcnZlcjoge1xuICAgICAgcHJveHk6IHtcbiAgICAgICAgJy9hcGknOiBwcm94eU9wdGlvbnMsXG4gICAgICB9LFxuICAgIH0sXG4gICAgYnVpbGQ6IHtcbiAgICAgIGNzc0NvZGVTcGxpdDogZmFsc2UsXG4gICAgICByb2xsdXBPcHRpb25zOiB7XG4gICAgICAgIG91dHB1dDoge1xuICAgICAgICAgIG1hbnVhbENodW5rcyhpZCkge1xuICAgICAgICAgICAgaWYgKGlkLmluY2x1ZGVzKCd0aWt0b2tlbicpKSB7XG4gICAgICAgICAgICAgIHJldHVybiAndGlrdG9rZW4nO1xuICAgICAgICAgICAgfSBlbHNlIGlmIChpZC5pbmNsdWRlcygnd2ViL3NyYycpKSB7XG4gICAgICAgICAgICAgIHJldHVybiAnY2h1bmsnO1xuICAgICAgICAgICAgfSBlbHNlIGlmIChpZC5pbmNsdWRlcygnbmFpdmUnKSkge1xuICAgICAgICAgICAgICByZXR1cm4gJ25haXZlJztcbiAgICAgICAgICAgIH0gZWxzZSBpZiAoaWQuaW5jbHVkZXMoJ25vZGVfbW9kdWxlJykpIHtcbiAgICAgICAgICAgICAgcmV0dXJuICdkZXAnO1xuICAgICAgICAgICAgfVxuICAgICAgICAgIH0sXG4gICAgICAgIH0sXG4gICAgICB9LFxuICAgIH0sXG4gICAgcGx1Z2luczogW1xuICAgICAgdnVlKCksXG4gICAgICB3YXNtKCksXG4gICAgICB0b3BMZXZlbEF3YWl0KCksXG4gICAgICB0c2NvbmZpZ1BhdGhzKHsgbG9vc2U6IHRydWUgfSksXG4gICAgICBBdXRvSW1wb3J0KHtcbiAgICAgICAgaW1wb3J0czogW1xuICAgICAgICAgICd2dWUnLFxuICAgICAgICAgIHtcbiAgICAgICAgICAgICduYWl2ZS11aSc6IFtcbiAgICAgICAgICAgICAgJ3VzZURpYWxvZycsXG4gICAgICAgICAgICAgICd1c2VNZXNzYWdlJyxcbiAgICAgICAgICAgICAgJ3VzZU5vdGlmaWNhdGlvbicsXG4gICAgICAgICAgICAgICd1c2VMb2FkaW5nQmFyJyxcbiAgICAgICAgICAgIF0sXG4gICAgICAgICAgfSxcbiAgICAgICAgXSxcbiAgICAgIH0pLFxuICAgICAgQ29tcG9uZW50cyh7XG4gICAgICAgIHJlc29sdmVyczogW05haXZlVWlSZXNvbHZlcigpXSxcbiAgICAgICAgZGlyczogWycuLyoqL2NvbXBvbmVudHMvKionXSxcbiAgICAgIH0pLFxuICAgIF0sXG4gIH07XG59KTtcbiJdLAogICJtYXBwaW5ncyI6ICI7QUFBa1IsT0FBTyxTQUFTO0FBQ2xTLE9BQU8sZ0JBQWdCO0FBQ3ZCLFNBQVMsdUJBQXVCO0FBQ2hDLE9BQU8sZ0JBQWdCO0FBQ3ZCLFNBQXVCLGVBQWU7QUFDdEMsT0FBTyxtQkFBbUI7QUFDMUIsT0FBTyxVQUFVO0FBQ2pCLE9BQU8sbUJBQW1CO0FBQzFCLFNBQVMsb0JBQW9CO0FBRTdCLElBQU8sc0JBQVEsYUFBYSxDQUFDLEVBQUUsU0FBUyxLQUFLLE1BQU07QUFDakQsUUFBTSxNQUFNLFFBQVEsTUFBTSxRQUFRLElBQUksR0FBRyxPQUFPO0FBRWhELE1BQUk7QUFDSixNQUFJLFdBQVc7QUFDYixtQkFBZTtBQUFBLE1BQ2IsUUFBUTtBQUFBLE1BQ1IsY0FBYztBQUFBLE1BQ2QsU0FBUyxDQUFDLFNBQVMsS0FBSyxRQUFRLFVBQVUsRUFBRTtBQUFBLElBQzlDO0FBQUE7QUFFQSxtQkFBZTtBQUFBLE1BQ2IsUUFBUTtBQUFBLE1BQ1IsY0FBYztBQUFBLE1BQ2QsT0FBTyxLQUFLLE1BQU0sVUFBVTtBQUMxQixZQUNFLElBQUksT0FDSixJQUFJLElBQUksU0FBUyxhQUFhLEtBQzlCLElBQUksV0FBVyxPQUNmO0FBQ0EsY0FBSSxJQUFJLElBQUksU0FBUyxXQUFXLEdBQUc7QUFDakMsb0JBQVEsSUFBSSx3R0FBbUI7QUFDL0IsbUJBQU87QUFBQSxVQUNUO0FBQ0EsY0FBSSxJQUFJLElBQUksU0FBUyxTQUFTLEdBQUc7QUFDL0Isb0JBQVEsSUFBSSx3R0FBbUI7QUFDL0IsbUJBQU87QUFBQSxVQUNUO0FBQUEsUUFDRjtBQUFBLE1BQ0Y7QUFBQSxJQUNGO0FBRUYsU0FBTztBQUFBLElBQ0wsUUFBUTtBQUFBLE1BQ04sT0FBTztBQUFBLFFBQ0wsUUFBUTtBQUFBLE1BQ1Y7QUFBQSxJQUNGO0FBQUEsSUFDQSxPQUFPO0FBQUEsTUFDTCxjQUFjO0FBQUEsTUFDZCxlQUFlO0FBQUEsUUFDYixRQUFRO0FBQUEsVUFDTixhQUFhLElBQUk7QUFDZixnQkFBSSxHQUFHLFNBQVMsVUFBVSxHQUFHO0FBQzNCLHFCQUFPO0FBQUEsWUFDVCxXQUFXLEdBQUcsU0FBUyxTQUFTLEdBQUc7QUFDakMscUJBQU87QUFBQSxZQUNULFdBQVcsR0FBRyxTQUFTLE9BQU8sR0FBRztBQUMvQixxQkFBTztBQUFBLFlBQ1QsV0FBVyxHQUFHLFNBQVMsYUFBYSxHQUFHO0FBQ3JDLHFCQUFPO0FBQUEsWUFDVDtBQUFBLFVBQ0Y7QUFBQSxRQUNGO0FBQUEsTUFDRjtBQUFBLElBQ0Y7QUFBQSxJQUNBLFNBQVM7QUFBQSxNQUNQLElBQUk7QUFBQSxNQUNKLEtBQUs7QUFBQSxNQUNMLGNBQWM7QUFBQSxNQUNkLGNBQWMsRUFBRSxPQUFPLEtBQUssQ0FBQztBQUFBLE1BQzdCLFdBQVc7QUFBQSxRQUNULFNBQVM7QUFBQSxVQUNQO0FBQUEsVUFDQTtBQUFBLFlBQ0UsWUFBWTtBQUFBLGNBQ1Y7QUFBQSxjQUNBO0FBQUEsY0FDQTtBQUFBLGNBQ0E7QUFBQSxZQUNGO0FBQUEsVUFDRjtBQUFBLFFBQ0Y7QUFBQSxNQUNGLENBQUM7QUFBQSxNQUNELFdBQVc7QUFBQSxRQUNULFdBQVcsQ0FBQyxnQkFBZ0IsQ0FBQztBQUFBLFFBQzdCLE1BQU0sQ0FBQyxvQkFBb0I7QUFBQSxNQUM3QixDQUFDO0FBQUEsSUFDSDtBQUFBLEVBQ0Y7QUFDRixDQUFDOyIsCiAgIm5hbWVzIjogW10KfQo=
