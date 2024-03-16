import { defineStore } from 'pinia';

import { UserProfile, UserRole } from '@/model/User';

interface UserData {
  info?: UserProfile;
  renewedAt?: number;
  adminMode: boolean;
}

const validExpires = (info: UserProfile | undefined) => {
  if (info) {
    if (Date.now() / 1000 > info.expiresAt) {
      return undefined;
    } else {
      return info;
    }
  } else {
    return undefined;
  }
};

const userRoleAtLeast = (info: UserProfile | undefined, role: UserRole) => {
  const myRole = validExpires(info)?.role;
  if (!myRole) {
    return false;
  }

  const roleToNumber: Map<UserRole, number> = new Map([
    ['admin', 4],
    ['maintainer', 3],
    ['trusted', 2],
    ['normal', 1],
    ['banned', 0],
  ]);
  if (myRole === undefined) {
    return false;
  } else {
    return roleToNumber.get(myRole)! >= roleToNumber.get(role)!;
  }
};

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
    role: ({ info }) => validExpires(info)?.role,
    passWeek: ({ info }) => {
      const createAt = validExpires(info)?.createAt;
      if (createAt) {
        return Date.now() / 1000 - createAt > 7 * 24 * 3600;
      } else {
        return false;
      }
    },
    isOldAss: ({ info }) => {
      const createAt = validExpires(info)?.createAt;
      if (createAt) {
        return Date.now() / 1000 - createAt > 30 * 24 * 3600;
      } else {
        return false;
      }
    },
    isAdmin: ({ info }) => userRoleAtLeast(info, 'admin'),
    isMaintainer: ({ info }) => userRoleAtLeast(info, 'maintainer'),
    isTrusted: ({ info }) => userRoleAtLeast(info, 'trusted'),
    asAdmin: ({ adminMode, info }) =>
      adminMode && userRoleAtLeast(info, 'admin'),
  },
  actions: {
    setProfile(profile: UserProfile) {
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
