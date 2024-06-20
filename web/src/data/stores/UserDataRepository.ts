import { UserRole } from '@/model/User';
import { useLocalStorage } from '@vueuse/core';
import { updateToken } from '../api/client';
import { Locator } from '..';
import { AuthRepository } from '../api';

interface UserProfile {
  id: string;
  email: string;
  username: string;
  role: UserRole;
  createAt: number;
  token: string;
  expireAt: number;
}

interface UserData {
  profile?: UserProfile;
  renewedAt?: number;
  adminMode: boolean;
}

export const createUserDataRepository = () => {
  const ref = useLocalStorage<UserData>('authInfo', {
    profile: undefined,
    renewedAt: undefined,
    adminMode: false,
  });

  if (ref.value.profile && Date.now() / 1000 > ref.value.profile.expireAt) {
    ref.value.profile = undefined;
  }

  const isSignedIn = computed(() => ref.value.profile !== undefined);

  const createAtLeast = (days: number) => {
    const createAt = ref.value.profile?.createAt;
    if (!createAt) {
      return false;
    }
    return Date.now() / 1000 - createAt > days * 24 * 3600;
  };

  const createAtLeastOneMonth = computed(() => createAtLeast(30));

  const userRoleAtLeast = (role: UserRole) => {
    const myRole = ref.value.profile?.role;
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
    return roleToNumber.get(myRole)! >= roleToNumber.get(role)!;
  };

  const atLeastAdmin = computed(() => userRoleAtLeast('admin'));
  const atLeastMaintainer = computed(() => userRoleAtLeast('maintainer'));
  const asAdmin = computed(() => atLeastAdmin.value && ref.value.adminMode);

  const setProfile = (token: string) => {
    const part = token.split('.')[1];
    const { id, email, username, role, createAt, exp } = JSON.parse(
      atob(part),
    ) as {
      id: string;
      email: string;
      username: string;
      role: UserRole;
      createAt: number;
      exp: number;
    };
    const profile: UserProfile = {
      id,
      email,
      username,
      role,
      createAt,
      token,
      expireAt: exp,
    };

    ref.value.renewedAt = Date.now();
    ref.value.profile = profile;
  };
  const deleteProfile = () => {
    ref.value.profile = undefined;
  };
  const toggleAdminMode = () => {
    ref.value.adminMode = !ref.value.adminMode;
  };

  migrate(ref.value, setProfile);

  return {
    userData: ref,
    isSignedIn,
    //
    createAtLeastOneMonth,
    //
    atLeastAdmin,
    atLeastMaintainer,
    asAdmin,
    //
    setProfile,
    deleteProfile,
    toggleAdminMode,
  };
};

const migrate = (userData: UserData, setProfile: (token: string) => void) => {
  // 2024-06-21
  if ((userData as any).info !== undefined) {
    //  30天后可删除
    updateToken((userData as any).info.token);
    AuthRepository.renew().then((token) => setProfile(token));
    delete (userData as any).info;
  }
};
