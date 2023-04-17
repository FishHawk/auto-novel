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
  { path: '/', component: () => import('./views/Query.vue') },
  { path: '/feedback', component: () => import('./views/Feedback.vue') },
  { path: '/how-to-use', component: () => import('./views/HowToUse.vue') },

  { path: '/novel-list', component: () => import('./views/WebNovelList.vue') },
  {
    path: '/novel-rank/:providerId/:typeId',
    component: () => import('./views/WebNovelRank.vue'),
  },
  {
    path: '/novel/:providerId/:bookId',
    component: () => import('./views/WebNovelMetadata.vue'),
  },
  {
    path: '/novel/:providerId/:bookId/:episodeId',
    component: () => import('./views/WebNovelEpisode.vue'),
  },

  {
    path: '/admin',
    redirect: '/admin/patch',
    beforeEnter: requireAtLeastMaintainer,
    children: [
      {
        path: '/admin/patch',
        component: () => import('./views/AdminPatch.vue'),
      },
      {
        path: '/admin/toc-merge',
        component: () => import('./views/AdminTocMerge.vue'),
      },
      {
        path: '/admin/patch/:providerId/:bookId',
        component: () => import('./views/AdminPatchDetail.vue'),
      },
    ],
  },

  {
    path: '/wenku-list',
    component: () => import('./views/WenkuNovelList.vue'),
  },
  {
    path: '/wenku/:bookId',
    component: () => import('./views/WenkuNovelMetadata.vue'),
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
