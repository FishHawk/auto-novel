import { defineStore } from 'pinia';
import { SignInDto } from '../api/api_auth';

export type AuthInfo = SignInDto;

function validExpires(info: AuthInfo | undefined) {
  if (info) {
    if (Date.now() / 1000 > info.expiresAt) {
      return undefined;
    } else {
      return info;
    }
  } else {
    return undefined;
  }
}

export const useAuthInfoStore = defineStore('authInfo', {
  state: () => ({
    info: undefined as AuthInfo | undefined,
  }),
  getters: {
    username: (state) => validExpires(state.info)?.username,
    role: (state) => validExpires(state.info)?.role,
    token: (state) => validExpires(state.info)?.token,
  },
  actions: {
    set(info: AuthInfo) {
      this.info = info;
    },
    delete() {
      this.info = undefined;
    },
  },
  persist: true,
});

export function atLeastMaintainer(
  role: 'normal' | 'admin' | 'maintainer' | undefined
) {
  return role === 'admin' || role === 'maintainer';
}
