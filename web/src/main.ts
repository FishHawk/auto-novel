import { createPinia } from 'pinia';
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate';
import { PiniaSharedState } from 'pinia-shared-state';
import { createApp } from 'vue';

import App from './App.vue';
import router from './router';

const pinia = createPinia()
  .use(PiniaSharedState({ initialize: true, type: 'localstorage' }))
  .use(piniaPluginPersistedstate);

createApp(App).use(pinia).use(router).mount('#app');
