import { createRouter, createWebHistory } from 'vue-router';

const history = createWebHistory();

const routes = [
  {
    path: '/',
    component: () => import('./views/Query.vue'),
  },
  {
    path: '/list',
    component: () => import('./views/List.vue'),
  },
  {
    path: '/novel/:providerId/:bookId',
    component: () => import('./views/NovelMetadata.vue'),
  },
  {
    path: '/novel/:providerId/:bookId/:episodeId',
    component: () => import('./views/NovelEpisode.vue'),
  },
  {
    path: '/novel-edit/:providerId/:bookId',
    component: () => import('./views/NovelEditMetadata.vue'),
  },
  {
    path: '/novel-edit/:providerId/:bookId/:episodeId',
    component: () => import('./views/NovelEditEpisode.vue'),
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
