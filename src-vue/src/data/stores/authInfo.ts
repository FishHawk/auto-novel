import { defineStore } from 'pinia';

export interface AuthInfo {
  email: string;
  username: string;
  token: string;
  expiresAt: number;
}

function validExpires(info: AuthInfo) {
  if (Date.now() / 1000 > info.expiresAt) {
    return undefined;
  } else {
    return info;
  }
}

export const useAuthInfoStore = defineStore('authInfo', {
  state: () => ({
    info: undefined as AuthInfo | undefined,
  }),
  getters: {
    username: (state) => {
      if (state.info) {
        return validExpires(state.info)?.username;
      } else {
        return undefined;
      }
    },
    token: (state) => {
      if (state.info) {
        return validExpires(state.info)?.token;
      } else {
        return undefined;
      }
    },
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
