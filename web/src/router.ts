import { createRouter, createWebHistory } from 'vue-router';

const history = createWebHistory();

const router = createRouter({
  history,
  routes: [
    {
      path: '/',
      meta: { title: '首页' },
      component: () => import('./pages/home/Index.vue'),
    },

    {
      path: '/reset-password',
      meta: { title: '重置密码' },
      component: () => import('./pages/other/ResetPassword.vue'),
    },

    {
      path: '/favorite-list',
      meta: { title: '我的收藏' },
      component: () => import('./pages/list/FavoriteList.vue'),
    },
    {
      path: '/read-history',
      meta: { title: '阅读历史' },
      component: () => import('./pages/list/ReadHistoryList.vue'),
    },
    {
      path: '/novel-list',
      meta: { title: '网络小说' },
      component: () => import('./pages/list/WebNovelList.vue'),
    },
    {
      path: '/wenku-list',
      meta: { title: '文库小说' },
      component: () => import('./pages/list/WenkuNovelList.vue'),
    },
    {
      path: '/novel-rank/:providerId/:typeId',
      meta: { title: '排行榜' },
      component: () => import('./pages/list/WebNovelRank.vue'),
    },
    {
      path: '/wenku/non-archived',
      meta: { title: '文件翻译' },
      component: () => import('./pages/novel/WenkuNonArchived.vue'),
    },

    {
      path: '/novel/:providerId/:novelId',
      component: () => import('./pages/novel/WebNovel.vue'),
    },
    {
      path: '/wenku/:novelId',
      component: () => import('./pages/novel/WenkuNovel.vue'),
    },

    {
      path: '/novel/:providerId/:novelId/:chapterId',
      component: () => import('./pages/novel/WebChapter.vue'),
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
      component: () => import('./pages/forum/ForumEdit.vue'),
    },
    {
      path: '/forum-edit/:id',
      meta: { title: '编辑文章' },
      component: () => import('./pages/forum/ForumEdit.vue'),
    },

    {
      path: '/admin',
      redirect: '/admin/web-patch-history',
      children: [
        {
          path: '/admin/web-patch-history',
          component: () => import('./pages/admin/AdminWebPatchHistory.vue'),
        },
        {
          path: '/admin/web-toc-merge-history',
          component: () => import('./pages/admin/AdminWebTocMergeHistory.vue'),
        },
        {
          path: '/admin/wenku-upload-history',
          component: () => import('./pages/admin/AdminWenkuUploadHistory.vue'),
        },
      ],
    },
  ],
  scrollBehavior(to, from, savedPosition) {
    return { top: 0 };
  },
});

router.beforeEach((to, _from) => {
  if (to.meta.title) {
    document.title = (to.meta.title as string) + ' | 轻小说机翻机器人';
  }
});

export default router;
