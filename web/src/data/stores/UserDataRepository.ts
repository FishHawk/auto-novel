import { UserProfile, UserRole } from '@/model/User';
import { useLocalStorage } from '@vueuse/core';

interface UserData {
  info?: UserProfile;
  renewedAt?: number;
  adminMode: boolean;
}

export const createUserDataRepository = () => {
  const ref = useLocalStorage<UserData>('authInfo', {
    info: undefined,
    renewedAt: undefined,
    adminMode: false,
  });

  if (ref.value.info && Date.now() / 1000 > ref.value.info.expiresAt) {
    ref.value.info = undefined;
  }

  const isSignedIn = computed(() => ref.value.info !== undefined);

  const createAtLeast = (days: number) => {
    const createAt = ref.value.info?.createAt;
    if (!createAt) {
      return false;
    }
    return Date.now() / 1000 - createAt > days * 24 * 3600;
  };

  const createAtLeastOneMonth = computed(() => createAtLeast(30));

  const userRoleAtLeast = (role: UserRole) => {
    const myRole = ref.value.info?.role;
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

  const setProfile = (profile: UserProfile) => {
    ref.value.renewedAt = Date.now();
    ref.value.info = profile;
  };
  const deleteProfile = () => {
    ref.value.info = undefined;
  };
  const toggleAdminMode = () => {
    ref.value.adminMode = !ref.value.adminMode;
  };

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
