import { createRouter, createWebHistory } from 'vue-router';

const history = createWebHistory();

const routes = [
  { path: '/', component: () => import('./views/Query.vue') },
  { path: '/feedback', component: () => import('./views/Feedback.vue') },
  { path: '/extra', component: () => import('./views/Extra.vue') },

  { path: '/list', component: () => import('./views/List.vue') },
  {
    path: '/rank/:providerId/:type',
    component: () => import('./views/Rank.vue'),
  },
  { path: '/patch', component: () => import('./views/Patch.vue') },

  {
    path: '/novel/:providerId/:bookId',
    component: () => import('./views/NovelMetadata.vue'),
  },
  {
    path: '/novel/:providerId/:bookId/:episodeId',
    component: () => import('./views/NovelEpisode.vue'),
  },
  {
    path: '/patch/:providerId/:bookId',
    component: () => import('./views/PatchDetail.vue'),
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
