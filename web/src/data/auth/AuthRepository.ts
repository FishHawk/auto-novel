import { jwtDecode } from 'jwt-decode';

import { UserRole } from '@/model/User';
import { useLocalStorage } from '@vueuse/core';

import { formatError } from '@/data/api';
import { updateToken } from '@/data/api/client';
import { AuthProfile } from './Auth';
import { AuthApi, SignInBody, SignUpBody } from './AuthApi';

type AuthProfileFull = AuthProfile & {
  token: string;
  expireAt: number;
};

interface AuthData {
  profile?: AuthProfileFull;
  renewedAt?: number;
  adminMode: boolean;
}

export const createAuthRepository = () => {
  const authData = useLocalStorage<AuthData>('authInfo', {
    profile: undefined,
    renewedAt: undefined,
    adminMode: false,
  });

  const profile = computed<AuthProfile | undefined>(
    () => authData.value.profile,
  );

  const isSignedIn = computed(() => profile.value !== undefined);

  const createAtLeastOneMonth = computed(() => {
    const days = 30;
    const createAt = authData.value.profile?.createAt;
    if (!createAt) return false;
    return Date.now() / 1000 - createAt > days * 24 * 3600;
  });

  const userRoleAtLeast = (role: UserRole) => {
    const myRole = authData.value.profile?.role;
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
  const asAdmin = computed(
    () => atLeastAdmin.value && authData.value.adminMode,
  );

  const toggleAdminMode = () => {
    authData.value.adminMode = !authData.value.adminMode;
  };

  //
  const setProfile = (token: string) => {
    const { id, email, username, role, createAt, exp } = jwtDecode<{
      id: string;
      email: string;
      username: string;
      role: UserRole;
      createAt: number;
      exp: number;
    }>(token);
    const profile: AuthProfileFull = {
      id,
      email,
      username,
      role,
      createAt,
      token,
      expireAt: exp,
    };
    authData.value.renewedAt = Date.now();
    authData.value.profile = profile;
  };

  const activateAuth = async () => {
    // 检查是否过期
    if (
      authData.value.profile &&
      Date.now() / 1000 > authData.value.profile.expireAt
    ) {
      authData.value.profile = undefined;
    }

    // 订阅Token
    watch(
      () => authData.value.profile?.token,
      (token) => updateToken(token),
      { immediate: true },
    );

    // 更新Token，冷却时间为24小时
    const renewCooldown = 24 * 3600 * 1000;
    if (authData.value.profile) {
      const sinceLoggedIn = Date.now() - (authData.value.renewedAt ?? 0);
      if (sinceLoggedIn > renewCooldown) {
        await AuthApi.renew()
          .then((token) => setProfile(token))
          .catch(async (e) => {
            console.warn('更新授权失败：' + (await formatError(e)));
          });
      }
    }

    // 2024-06-21
    if ((authData as any).info !== undefined) {
      //  30天后可删除
      updateToken((authData as any).info.token);
      await AuthApi.renew().then((token) => {
        setProfile(token);
        delete (authData as any).info;
      });
    }

    // 2024-06-26 createAt不停变小
    if (authData.value.profile) {
      if (authData.value.profile.createAt < 2000000) {
        authData.value.profile = undefined;
      }
    }
  };

  const signUp = async (json: SignUpBody) => {
    const token = await AuthApi.signUp(json);
    setProfile(token);
  };
  const signIn = async (json: SignInBody) => {
    const token = await AuthApi.signIn(json);
    setProfile(token);
  };
  const signOut = () => {
    authData.value.profile = undefined;
  };

  return {
    profile,
    isSignedIn,
    //
    createAtLeastOneMonth,
    //
    atLeastAdmin,
    atLeastMaintainer,
    asAdmin,
    //
    setProfile,
    toggleAdminMode,
    //
    activateAuth,
    signUp,
    signIn,
    signOut,
    verifyEmail: AuthApi.verifyEmail,
    sendResetPasswordEmail: AuthApi.sendResetPasswordEmail,
    resetPassword: AuthApi.resetPassword,
  };
};
