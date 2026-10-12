import { createWebKit } from '@novelia/web-kit';
import { PiniaColada } from '@pinia/colada';

import App from './App.vue';
import router from './router';
import './style.css';

const app = createApp(App);

export const webKit = createWebKit({
  auth: {
    app: 'n',
    url: __AUTH_URL__,
  },
  brand: '小说',
  repository: {
    url: 'https://github.com/auto-novel/auto-novel',
    buildTime: __BUILD_INFO__.buildTime,
    commitSha: __BUILD_INFO__.gitCommit,
  },
});

const pinia = createPinia();
app.use(pinia);
app.use(PiniaColada, {
  queryOptions: {
    gcTime: 3600_000,
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    refetchOnReconnect: true,
  },
});

app.use(webKit);
app.use(router);
app.mount('#app');
