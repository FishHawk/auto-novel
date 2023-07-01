import { createRouter, createWebHistory } from 'vue-router';
import { atLeastMaintainer, useAuthInfoStore } from './data/stores/authInfo';

const history = createWebHistory();

function requireAtLeastMaintainer(from: any, to: any, next: any) {
  if (atLeastMaintainer(useAuthInfoStore().role)) {
    next();
  } else {
    alert('没有管理员权限，无法访问页面');
    next({ path: '/' });
  }
}

const routes = [
  { path: '/', component: () => import('./pages/home/Index.vue') },

  { path: '/feedback', component: () => import('./pages/other/Feedback.vue') },
  { path: '/donate', component: () => import('./pages/other/Donate.vue') },
  {
    path: '/reset-password',
    component: () => import('./pages/other/ResetPassword.vue'),
  },
  {
    path: '/how-to-use',
    component: () => import('./pages/other/HowToUse.vue'),
  },

  {
    path: '/favorite-list',
    component: () => import('./pages/list/FavoriteList.vue'),
  },
  {
    path: '/read-history',
    component: () => import('./pages/list/ReadHistoryList.vue'),
  },
  {
    path: '/novel-list',
    component: () => import('./pages/list/WebNovelList.vue'),
  },
  {
    path: '/novel-rank/:providerId/:typeId',
    component: () => import('./pages/list/WebNovelRank.vue'),
  },
  {
    path: '/wenku-list',
    component: () => import('./pages/list/WenkuNovelList.vue'),
  },

  {
    path: '/novel/:providerId/:novelId',
    component: () => import('./pages/novel/WebNovel.vue'),
  },
  {
    path: '/wenku/non-archived',
    component: () => import('./pages/novel/WenkuNonArchived.vue'),
  },
  {
    path: '/wenku/:novelId',
    component: () => import('./pages/novel/WenkuNovel.vue'),
  },

  {
    path: '/novel/:providerId/:novelId/:chapterId',
    component: () => import('./pages/chapter/WebChapter.vue'),
  },

  {
    path: '/admin',
    redirect: '/admin/patch',
    beforeEnter: requireAtLeastMaintainer,
    children: [
      {
        path: '/admin/patch',
        component: () => import('./pages/admin/AdminPatch.vue'),
      },
      {
        path: '/admin/toc-merge',
        component: () => import('./pages/admin/AdminTocMerge.vue'),
      },
      {
        path: '/admin/wenku-upload-history',
        component: () => import('./pages/admin/AdminWenkuUploadHistory.vue'),
      },
    ],
  },
];

const router = createRouter({
  history,
  routes,
  scrollBehavior(to, from, savedPosition) {
    return { top: 0 };
  },
});

export default router;
