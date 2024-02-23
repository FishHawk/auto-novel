import { createPinia } from 'pinia';
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate';
import { PiniaSharedState } from 'pinia-shared-state';
import { createApp } from 'vue';

import App from './App.vue';
import router from './router';

const pinia = createPinia()
  .use(PiniaSharedState({ initialize: true, type: 'localstorage' }))
  .use(piniaPluginPersistedstate);

const app = createApp(App);
app.use(pinia);
app.use(router);

app.config.errorHandler = (err) => {
  console.log(err);
  const div = document.createElement('div');
  div.innerHTML = `Vue错误:  ${String(err)
    .replace('<', '&lt;')
    .replace('>', '&gt;')}`;
  document.body.appendChild(div);
};

app.mount('#app');
