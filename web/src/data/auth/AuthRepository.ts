import { jwtDecode } from 'jwt-decode';

import { formatError } from '@/data/api';
import { updateToken } from '@/data/api/client';
import { UserRole } from '@/model/User';
import { useLocalStorage } from '@/util';

import { AuthApi, SignInBody, SignUpBody } from './AuthApi';
import { LSKey } from '../LocalStorage';

interface AuthProfile {
  id: string;
  email: string;
  username: string;
  role: UserRole;
  createAt: number;
  token: string;
  expireAt: number;
}

interface AuthData {
  profile?: AuthProfile;
  renewedAt?: number;
  adminMode: boolean;
}

export const createAuthRepository = () => {
  const authData = useLocalStorage<AuthData>(LSKey.Auth, {
    profile: undefined,
    renewedAt: undefined,
    adminMode: false,
  });

  const whoami = computed(() => {
    const { profile, adminMode: manageMode } = authData.value;

    const roleToNumber = {
      admin: 4,
      maintainer: 3,
      trusted: 2,
      normal: 1,
    };
    const roleNumber = profile ? roleToNumber[profile.role] : 0;

    const isAdmin = roleNumber >= roleToNumber.admin;
    const isMaintainer = roleNumber >= roleToNumber.maintainer;
    const isSignedIn = roleNumber >= roleToNumber.normal;

    const createAtLeast = (days: number) => {
      if (!profile) return false;
      return Date.now() / 1000 - profile.createAt > days * 24 * 3600;
    };

    return {
      username: profile?.username,
      role: profile?.role,
      createAt: profile?.createAt,
      isAdmin,
      isMaintainer,
      isSignedIn,
      asAdmin: isAdmin && manageMode,
      asMaintainer: isMaintainer && manageMode,
      allowNsfw: createAtLeast(30),
      allowAdvancedFeatures: createAtLeast(30),
      isMe: (username: string) => profile?.username === username,
    };
  });

  const toggleManageMode = () => {
    authData.value.adminMode = !authData.value.adminMode;
  };

  const setProfile = (token: string) => {
    const { id, email, username, role, createAt, exp } = jwtDecode<{
      id: string;
      email: string;
      username: string;
      role: UserRole;
      createAt: number;
      exp: number;
    }>(token);
    authData.value.renewedAt = Date.now();
    authData.value.profile = {
      id,
      email,
      username,
      role,
      createAt,
      token,
      expireAt: exp,
    };
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
    whoami,
    toggleManageMode,
    activateAuth,
    signUp,
    signIn,
    signOut,
    verifyEmail: AuthApi.verifyEmail,
    sendResetPasswordEmail: AuthApi.sendResetPasswordEmail,
    resetPassword: AuthApi.resetPassword,
  };
};
