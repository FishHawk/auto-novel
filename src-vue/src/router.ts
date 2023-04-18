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
  {
    path: '/how-to-use',
    component: () => import('./pages/other/HowToUse.vue'),
  },

  {
    path: '/favorite-list',
    component: () => import('./pages/list/FavoriteList.vue'),
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
    path: '/novel/:providerId/:bookId',
    component: () => import('./pages/metadata/WebMetadata.vue'),
  },
  {
    path: '/wenku/:bookId',
    component: () => import('./pages/metadata/WenkuMetadata.vue'),
  },

  {
    path: '/novel/:providerId/:bookId/:episodeId',
    component: () => import('./pages/episode/NovelEpisode.vue'),
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
        path: '/admin/patch/:providerId/:bookId',
        component: () => import('./pages/admin/AdminPatchDetail.vue'),
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

// router.beforeEach(async (to, from) => {
//   if (!isAuthenticated && to.name !== 'Login') {
//     // redirect the user to the login page
//     return { name: 'Login' };
//   }
// });

export default router;
