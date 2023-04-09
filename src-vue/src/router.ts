import { createRouter, createWebHistory } from 'vue-router';

const history = createWebHistory();

const routes = [
  { path: '/', component: () => import('./views/Query.vue') },
  { path: '/feedback', component: () => import('./views/Feedback.vue') },
  { path: '/how-to-use', component: () => import('./views/HowToUse.vue') },

  { path: '/novel-list', component: () => import('./views/WebNovelList.vue') },
  {
    path: '/novel-rank/:providerId/:type',
    component: () => import('./views/WebNovelRank.vue'),
  },
  { path: '/patch', component: () => import('./views/Patch.vue') },
  {
    path: '/novel/:providerId/:bookId',
    component: () => import('./views/WebNovelMetadata.vue'),
  },
  {
    path: '/novel/:providerId/:bookId/:episodeId',
    component: () => import('./views/WebNovelEpisode.vue'),
  },
  {
    path: '/patch/:providerId/:bookId',
    component: () => import('./views/PatchDetail.vue'),
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

export default router;
