import { AuthRepository } from '@/data/api';

import { useUserDataStore } from '@/data/stores/user_data';
import { formatError } from '@/data/api/client';

export const AuthService = {
  activate: async () => {
    const userData = useUserDataStore();

    // 订阅Token
    const token = userData.token;
    AuthRepository.updateToken(token);
    userData.$subscribe((_mutation, state) => {
      AuthRepository.updateToken(state.info?.token);
    });

    // 更新授权
    const renewCooldown = 24 * 3600 * 1000; // 冷却时间为24小时
    if (userData.isLoggedIn) {
      const sinceLoggedIn = Date.now() - (userData.renewedAt ?? 0);
      if (!userData.info?.createAt || sinceLoggedIn > renewCooldown) {
        try {
          const profile = await AuthRepository.renew();
          userData.setProfile(profile);
        } catch (e) {
          console.warn('更新授权失败：' + formatError(e));
        }
      }
    }
  },

  signIn: (...args: Parameters<typeof AuthRepository.signIn>) =>
    AuthRepository.signIn(...args).then((profile) => {
      useUserDataStore().setProfile(profile);
    }),

  signUp: (...args: Parameters<typeof AuthRepository.signUp>) =>
    AuthRepository.signUp(...args).then((profile) => {
      useUserDataStore().setProfile(profile);
    }),
};
