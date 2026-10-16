import { createRouter, createWebHistory } from 'vue-router';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/banners' },
    {
      path: '/banners',
      name: 'banners',
      meta: { title: 'Banner 管理' },
      component: () => import('./views/BannersView.vue'),
    },
    { path: '/:pathMatch(.*)*', redirect: '/banners' },
  ],
});

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '管理后台')} · 轻小说机翻机器人`;
});

export default router;
