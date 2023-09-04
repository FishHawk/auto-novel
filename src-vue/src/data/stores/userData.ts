import { defineStore } from 'pinia';
import { SignInDto } from '../api/api_auth';

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
  adminMode: boolean;
}

export const useUserDataStore = defineStore('authInfo', {
  state: () =>
    <UserData>{
      info: undefined,
      adminMode: false,
    },
  getters: {
    logined: ({ info }) => validExpires(info) !== undefined,
    username: ({ info }) => validExpires(info)?.username,
    token: ({ info }) => validExpires(info)?.token,
    isAdmin: ({ info }) => validExpires(info)?.role === 'admin',
    asAdmin: ({ adminMode, info }) =>
      adminMode && validExpires(info)?.role === 'admin',
  },
  actions: {
    setProfile(profile: SignInDto) {
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