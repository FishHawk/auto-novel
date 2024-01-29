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
      target: "https://books.fishhawk.top",
      changeOrigin: true
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
            } else if (id.includes("data/translator") || id.includes("crypto") || id.includes("uuid") || id.includes("nanoid")) {
              return "translator";
            } else if (id.includes("web/src") || id.includes("naive")) {
              return "chunk1";
            } else if (id.includes("node_module")) {
              return "chunk2";
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
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcudHMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCIvaG9tZS93aC9Qcm9qZWN0cy9hdXRvLW5vdmVsL3dlYlwiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9maWxlbmFtZSA9IFwiL2hvbWUvd2gvUHJvamVjdHMvYXV0by1ub3ZlbC93ZWIvdml0ZS5jb25maWcudHNcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfaW1wb3J0X21ldGFfdXJsID0gXCJmaWxlOi8vL2hvbWUvd2gvUHJvamVjdHMvYXV0by1ub3ZlbC93ZWIvdml0ZS5jb25maWcudHNcIjtpbXBvcnQgdnVlIGZyb20gJ0B2aXRlanMvcGx1Z2luLXZ1ZSc7XG5pbXBvcnQgQXV0b0ltcG9ydCBmcm9tICd1bnBsdWdpbi1hdXRvLWltcG9ydC92aXRlJztcbmltcG9ydCB7IE5haXZlVWlSZXNvbHZlciB9IGZyb20gJ3VucGx1Z2luLXZ1ZS1jb21wb25lbnRzL3Jlc29sdmVycyc7XG5pbXBvcnQgQ29tcG9uZW50cyBmcm9tICd1bnBsdWdpbi12dWUtY29tcG9uZW50cy92aXRlJztcbmltcG9ydCB7IFByb3h5T3B0aW9ucywgbG9hZEVudiB9IGZyb20gJ3ZpdGUnO1xuaW1wb3J0IHRvcExldmVsQXdhaXQgZnJvbSAndml0ZS1wbHVnaW4tdG9wLWxldmVsLWF3YWl0JztcbmltcG9ydCB3YXNtIGZyb20gJ3ZpdGUtcGx1Z2luLXdhc20nO1xuaW1wb3J0IHRzY29uZmlnUGF0aHMgZnJvbSAndml0ZS10c2NvbmZpZy1wYXRocyc7XG5pbXBvcnQgeyBkZWZpbmVDb25maWcgfSBmcm9tICd2aXRlc3QvY29uZmlnJztcblxuZXhwb3J0IGRlZmF1bHQgZGVmaW5lQ29uZmlnKCh7IGNvbW1hbmQsIG1vZGUgfSkgPT4ge1xuICBjb25zdCBlbnYgPSBsb2FkRW52KG1vZGUsIHByb2Nlc3MuY3dkKCksICdMT0NBTCcpO1xuXG4gIGxldCBwcm94eU9wdGlvbnM6IFByb3h5T3B0aW9ucztcbiAgaWYgKCdMT0NBTCcgaW4gZW52KVxuICAgIHByb3h5T3B0aW9ucyA9IHtcbiAgICAgIHRhcmdldDogJ2h0dHA6Ly9sb2NhbGhvc3Q6ODA4MScsXG4gICAgICBjaGFuZ2VPcmlnaW46IHRydWUsXG4gICAgICByZXdyaXRlOiAocGF0aCkgPT4gcGF0aC5yZXBsYWNlKC9eXFwvYXBpLywgJycpLFxuICAgIH07XG4gIGVsc2VcbiAgICBwcm94eU9wdGlvbnMgPSB7XG4gICAgICB0YXJnZXQ6ICdodHRwczovL2Jvb2tzLmZpc2hoYXdrLnRvcCcsXG4gICAgICBjaGFuZ2VPcmlnaW46IHRydWUsXG4gICAgfTtcblxuICByZXR1cm4ge1xuICAgIHNlcnZlcjoge1xuICAgICAgcHJveHk6IHtcbiAgICAgICAgJy9hcGknOiBwcm94eU9wdGlvbnMsXG4gICAgICB9LFxuICAgIH0sXG4gICAgYnVpbGQ6IHtcbiAgICAgIGNzc0NvZGVTcGxpdDogZmFsc2UsXG4gICAgICByb2xsdXBPcHRpb25zOiB7XG4gICAgICAgIG91dHB1dDoge1xuICAgICAgICAgIG1hbnVhbENodW5rcyhpZCkge1xuICAgICAgICAgICAgaWYgKGlkLmluY2x1ZGVzKCd0aWt0b2tlbicpKSB7XG4gICAgICAgICAgICAgIHJldHVybiAndGlrdG9rZW4nO1xuICAgICAgICAgICAgfSBlbHNlIGlmIChcbiAgICAgICAgICAgICAgaWQuaW5jbHVkZXMoJ2RhdGEvdHJhbnNsYXRvcicpIHx8XG4gICAgICAgICAgICAgIGlkLmluY2x1ZGVzKCdjcnlwdG8nKSB8fFxuICAgICAgICAgICAgICBpZC5pbmNsdWRlcygndXVpZCcpIHx8XG4gICAgICAgICAgICAgIGlkLmluY2x1ZGVzKCduYW5vaWQnKVxuICAgICAgICAgICAgKSB7XG4gICAgICAgICAgICAgIHJldHVybiAndHJhbnNsYXRvcic7XG4gICAgICAgICAgICB9IGVsc2UgaWYgKGlkLmluY2x1ZGVzKCd3ZWIvc3JjJykgfHwgaWQuaW5jbHVkZXMoJ25haXZlJykpIHtcbiAgICAgICAgICAgICAgcmV0dXJuICdjaHVuazEnO1xuICAgICAgICAgICAgfSBlbHNlIGlmIChpZC5pbmNsdWRlcygnbm9kZV9tb2R1bGUnKSkge1xuICAgICAgICAgICAgICByZXR1cm4gJ2NodW5rMic7XG4gICAgICAgICAgICB9XG4gICAgICAgICAgfSxcbiAgICAgICAgfSxcbiAgICAgIH0sXG4gICAgfSxcbiAgICBwbHVnaW5zOiBbXG4gICAgICB2dWUoKSxcbiAgICAgIHdhc20oKSxcbiAgICAgIHRvcExldmVsQXdhaXQoKSxcbiAgICAgIHRzY29uZmlnUGF0aHMoeyBsb29zZTogdHJ1ZSB9KSxcbiAgICAgIEF1dG9JbXBvcnQoe1xuICAgICAgICBpbXBvcnRzOiBbXG4gICAgICAgICAgJ3Z1ZScsXG4gICAgICAgICAge1xuICAgICAgICAgICAgJ25haXZlLXVpJzogW1xuICAgICAgICAgICAgICAndXNlRGlhbG9nJyxcbiAgICAgICAgICAgICAgJ3VzZU1lc3NhZ2UnLFxuICAgICAgICAgICAgICAndXNlTm90aWZpY2F0aW9uJyxcbiAgICAgICAgICAgICAgJ3VzZUxvYWRpbmdCYXInLFxuICAgICAgICAgICAgXSxcbiAgICAgICAgICB9LFxuICAgICAgICBdLFxuICAgICAgfSksXG4gICAgICBDb21wb25lbnRzKHtcbiAgICAgICAgcmVzb2x2ZXJzOiBbTmFpdmVVaVJlc29sdmVyKCldLFxuICAgICAgICBkaXJzOiBbJy4vKiovY29tcG9uZW50cy8qKiddLFxuICAgICAgfSksXG4gICAgXSxcbiAgfTtcbn0pO1xuIl0sCiAgIm1hcHBpbmdzIjogIjtBQUFrUixPQUFPLFNBQVM7QUFDbFMsT0FBTyxnQkFBZ0I7QUFDdkIsU0FBUyx1QkFBdUI7QUFDaEMsT0FBTyxnQkFBZ0I7QUFDdkIsU0FBdUIsZUFBZTtBQUN0QyxPQUFPLG1CQUFtQjtBQUMxQixPQUFPLFVBQVU7QUFDakIsT0FBTyxtQkFBbUI7QUFDMUIsU0FBUyxvQkFBb0I7QUFFN0IsSUFBTyxzQkFBUSxhQUFhLENBQUMsRUFBRSxTQUFTLEtBQUssTUFBTTtBQUNqRCxRQUFNLE1BQU0sUUFBUSxNQUFNLFFBQVEsSUFBSSxHQUFHLE9BQU87QUFFaEQsTUFBSTtBQUNKLE1BQUksV0FBVztBQUNiLG1CQUFlO0FBQUEsTUFDYixRQUFRO0FBQUEsTUFDUixjQUFjO0FBQUEsTUFDZCxTQUFTLENBQUMsU0FBUyxLQUFLLFFBQVEsVUFBVSxFQUFFO0FBQUEsSUFDOUM7QUFBQTtBQUVBLG1CQUFlO0FBQUEsTUFDYixRQUFRO0FBQUEsTUFDUixjQUFjO0FBQUEsSUFDaEI7QUFFRixTQUFPO0FBQUEsSUFDTCxRQUFRO0FBQUEsTUFDTixPQUFPO0FBQUEsUUFDTCxRQUFRO0FBQUEsTUFDVjtBQUFBLElBQ0Y7QUFBQSxJQUNBLE9BQU87QUFBQSxNQUNMLGNBQWM7QUFBQSxNQUNkLGVBQWU7QUFBQSxRQUNiLFFBQVE7QUFBQSxVQUNOLGFBQWEsSUFBSTtBQUNmLGdCQUFJLEdBQUcsU0FBUyxVQUFVLEdBQUc7QUFDM0IscUJBQU87QUFBQSxZQUNULFdBQ0UsR0FBRyxTQUFTLGlCQUFpQixLQUM3QixHQUFHLFNBQVMsUUFBUSxLQUNwQixHQUFHLFNBQVMsTUFBTSxLQUNsQixHQUFHLFNBQVMsUUFBUSxHQUNwQjtBQUNBLHFCQUFPO0FBQUEsWUFDVCxXQUFXLEdBQUcsU0FBUyxTQUFTLEtBQUssR0FBRyxTQUFTLE9BQU8sR0FBRztBQUN6RCxxQkFBTztBQUFBLFlBQ1QsV0FBVyxHQUFHLFNBQVMsYUFBYSxHQUFHO0FBQ3JDLHFCQUFPO0FBQUEsWUFDVDtBQUFBLFVBQ0Y7QUFBQSxRQUNGO0FBQUEsTUFDRjtBQUFBLElBQ0Y7QUFBQSxJQUNBLFNBQVM7QUFBQSxNQUNQLElBQUk7QUFBQSxNQUNKLEtBQUs7QUFBQSxNQUNMLGNBQWM7QUFBQSxNQUNkLGNBQWMsRUFBRSxPQUFPLEtBQUssQ0FBQztBQUFBLE1BQzdCLFdBQVc7QUFBQSxRQUNULFNBQVM7QUFBQSxVQUNQO0FBQUEsVUFDQTtBQUFBLFlBQ0UsWUFBWTtBQUFBLGNBQ1Y7QUFBQSxjQUNBO0FBQUEsY0FDQTtBQUFBLGNBQ0E7QUFBQSxZQUNGO0FBQUEsVUFDRjtBQUFBLFFBQ0Y7QUFBQSxNQUNGLENBQUM7QUFBQSxNQUNELFdBQVc7QUFBQSxRQUNULFdBQVcsQ0FBQyxnQkFBZ0IsQ0FBQztBQUFBLFFBQzdCLE1BQU0sQ0FBQyxvQkFBb0I7QUFBQSxNQUM3QixDQUFDO0FBQUEsSUFDSDtBQUFBLEVBQ0Y7QUFDRixDQUFDOyIsCiAgIm5hbWVzIjogW10KfQo=
