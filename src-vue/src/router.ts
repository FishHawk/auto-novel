import { createRouter, createWebHistory } from 'vue-router';

const history = createWebHistory();

const routes = [
  { path: '/', component: () => import('./views/Query.vue') },

  { path: '/list', component: () => import('./views/List.vue') },
  { path: '/rank/syosetu/1', component: () => import('./views/List.vue') },
  { path: '/rank/syosetu/2', component: () => import('./views/List.vue') },
  { path: '/rank/syosetu/3', component: () => import('./views/List.vue') },

  // {
  //   path: '/list',
  //   component: () => import('./views/List.vue'),
  //   children: [
  //     { path: '/cached', component: UserProfile, },
  //     {
  //       path: '/rank/syosetu/1',
  //       component: UserPosts,
  //     },
  //   ],
  // },

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
  {
    path: '/patch',
    component: () => import('./views/PatchList.vue'),
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
