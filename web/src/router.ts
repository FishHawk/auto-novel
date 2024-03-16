import { createRouter, createWebHistory } from 'vue-router';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/*',
      component: () => import('./pages/layouts/AuthLayout.vue'),
      children: [
        {
          path: '/sign-in',
          meta: { title: '登录' },
          component: () => import('./pages/auth/SignIn.vue'),
        },
        {
          path: '/reset-password',
          meta: { title: '重置密码' },
          component: () => import('./pages/auth/ResetPassword.vue'),
        },
      ],
    },

    {
      path: '/*',
      component: () => import('./pages/layouts/ReaderLayout.vue'),
      children: [
        {
          path: '/novel/:providerId/:novelId/:chapterId',
          component: () => import('./pages/reader/Reader.vue'),
        },
        {
          path: '/workspace/reader/:novelId/:chapterId',
          component: () => import('./pages/reader/Reader.vue'),
        },
      ],
    },

    {
      path: '/*',
      component: () => import('./pages/layouts/MainLayout.vue'),
      children: [
        {
          path: '/',
          meta: { title: '首页' },
          component: () => import('./pages/home/Index.vue'),
        },

        {
          path: '/account',
          meta: { title: '账号中心' },
          component: () => import('./pages/other/AccountCenter.vue'),
        },

        {
          path: '/workspace',
          children: [
            {
              path: '',
              meta: { title: '工作区' },
              component: () => import('./pages/workspace/Workspace.vue'),
            },
            {
              path: 'katakana',
              meta: { title: '术语表工作区' },
              component: () => import('./pages/workspace/Katakana.vue'),
            },
            {
              path: 'gpt',
              meta: { title: 'GPT工作区' },
              component: () => import('./pages/workspace/GptWorkspace.vue'),
            },
            {
              path: 'sakura',
              meta: { title: 'Sakura工作区' },
              component: () => import('./pages/workspace/SakuraWorkspace.vue'),
            },
            {
              path: 'interactive',
              meta: { title: '交互翻译' },
              component: () => import('./pages/workspace/Interactive.vue'),
            },
          ],
        },

        {
          path: '/novel-list',
          meta: { title: '网络小说' },
          component: () => import('./pages/list/WebNovelList.vue'),
        },
        {
          path: '/novel-rank/:providerId/:typeId',
          meta: { title: '排行榜' },
          component: () => import('./pages/list/WebNovelRank.vue'),
        },

        {
          path: '/wenku-list',
          meta: { title: '文库小说' },
          component: () => import('./pages/list/WenkuNovelList.vue'),
        },

        {
          path: '/favorite',
          meta: { title: '我的收藏' },
          component: () => import('./pages/list/FavoriteList.vue'),
        },
        {
          path: '/read-history',
          meta: { title: '阅读历史' },
          component: () => import('./pages/list/ReadHistoryList.vue'),
        },

        {
          path: '/novel/:providerId/:novelId',
          component: () => import('./pages/novel/WebNovel.vue'),
        },
        {
          path: '/novel-edit/:providerId/:novelId',
          meta: { title: '编辑网络小说' },
          component: () => import('./pages/novel/WebNovelEdit.vue'),
        },

        {
          path: '/wenku/:novelId',
          component: () => import('./pages/novel/WenkuNovel.vue'),
        },
        {
          path: '/wenku-edit',
          meta: { title: '新建文库小说' },
          component: () => import('./pages/novel/WenkuNovelEdit.vue'),
        },
        {
          path: '/wenku-edit/:id',
          meta: { title: '编辑文库小说' },
          component: () => import('./pages/novel/WenkuNovelEdit.vue'),
        },

        {
          path: '/forum',
          meta: { title: '论坛' },
          component: () => import('./pages/forum/Forum.vue'),
        },
        {
          path: '/forum/:id',
          component: () => import('./pages/forum/ForumArticle.vue'),
        },
        {
          path: '/forum-edit',
          meta: { title: '发布文章' },
          component: () => import('./pages/forum/ForumArticleEdit.vue'),
        },
        {
          path: '/forum-edit/:id',
          meta: { title: '编辑文章' },
          component: () => import('./pages/forum/ForumArticleEdit.vue'),
        },

        {
          path: '/admin',
          component: () => import('./pages/layouts/AdminLayout.vue'),
          children: [
            {
              path: 'user',
              component: () => import('./pages/admin/AdminUserManagement.vue'),
            },
            {
              path: 'operation',
              component: () =>
                import('./pages/admin/AdminOperationHistory.vue'),
            },
            {
              path: 'web-toc-merge-history',
              component: () =>
                import('./pages/admin/AdminWebTocMergeHistory.vue'),
            },
          ],
        },

        // 兼容旧路由
        { path: '/personal', redirect: '/workspace' },
        { path: '/sakura-workspace', redirect: '/workspace/sakura' },

        // 404
        {
          path: '/:pathMatch(.*)',
          component: () => import('./pages/other/NotFound.vue'),
        },
      ],
    },
  ],

  scrollBehavior(_to, _from, _savedPosition) {
    return { top: 0 };
    //   return new Promise((resolve, _reject) => {
    //     if (savedPosition) {
    //       const resizeObserver = new ResizeObserver((entries) => {
    //         if (entries[0].target.clientHeight >= savedPosition.top) {
    //           resolve(savedPosition);
    //           resizeObserver.disconnect();
    //         }
    //       });
    //       resizeObserver.observe(document.body);
    //     } else {
    //       resolve({ top: 0 });
    //     }
    //   });
  },
});

router.beforeEach((to, _from) => {
  if (to.meta.title) {
    document.title = (to.meta.title as string) + ' | 轻小说机翻机器人';
  }
});

export default router;
