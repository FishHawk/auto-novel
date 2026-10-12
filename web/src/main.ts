import { PiniaColada } from '@pinia/colada';

import App from './App.vue';
import router from './router';
import { webKit } from './webKit';
import './style.css';

const app = createApp(App);

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
