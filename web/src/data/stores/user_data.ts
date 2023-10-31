import { defineStore } from 'pinia';

import { SignInDto } from '@/data/api/api_auth';

function validExpires(info: SignInDto | undefined) {
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

interface UserData {
  info?: SignInDto;
  renewedAt?: number;
  adminMode: boolean;
}

export const useUserDataStore = defineStore('authInfo', {
  state: () =>
    <UserData>{
      info: undefined,
      renewedAt: undefined,
      adminMode: false,
    },
  getters: {
    isLoggedIn: ({ info }) => validExpires(info) !== undefined,
    username: ({ info }) => validExpires(info)?.username,
    token: ({ info }) => validExpires(info)?.token,
    isOldAss: ({ info }) => {
      const createAt = validExpires(info)?.createAt;
      if (createAt) {
        return Date.now() / 1000 - createAt > 30 * 24 * 3600;
      } else {
        return false;
      }
    },
    isAdmin: ({ info }) => validExpires(info)?.role === 'admin',
    asAdmin: ({ adminMode, info }) =>
      adminMode && validExpires(info)?.role === 'admin',
  },
  actions: {
    setProfile(profile: SignInDto) {
      this.renewedAt = Date.now();
      this.info = profile;
    },
    deleteProfile() {
      this.info = undefined;
    },
    toggleAdminMode() {
      this.adminMode = !this.adminMode;
    },
  },
  persist: true,
});
