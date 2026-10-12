import { createWebKit } from '@novelia/web-kit';

// 模块单例同时供 Vue 和组件外的业务客户端使用。
export const webKit = createWebKit({
  auth: { app: 'n', url: __AUTH_URL__ },
  brand: '小说',
  repository: {
    url: 'https://github.com/auto-novel/auto-novel',
    buildTime: __BUILD_INFO__.buildTime,
    commitSha: __BUILD_INFO__.gitCommit,
  },
});
