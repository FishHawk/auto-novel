import { UserProfile } from '@/model/User';

import { client, updateToken } from './client';

interface SignInBody {
  emailOrUsername: string;
  password: string;
}

const signIn = (json: SignInBody) =>
  client.post(`auth/sign-in`, { json }).json<UserProfile>();

const renew = () => client.get(`auth/renew`).json<UserProfile>();

interface SignUpBody {
  email: string;
  emailCode: string;
  username: string;
  password: string;
}

const signUp = (json: SignUpBody) =>
  client.post('auth/sign-up', { json }).json<UserProfile>();

const verifyEmail = (email: string) =>
  client.post('auth/verify-email', {
    searchParams: { email },
  });

const sendResetPasswordEmail = (emailOrUsername: string) =>
  client.post('auth/reset-password-email', {
    searchParams: { emailOrUsername },
  });

const resetPassword = (
  emailOrUsername: string,
  token: string,
  password: string
) =>
  client.post('auth/reset-password', {
    searchParams: { emailOrUsername },
    json: { token, password },
  });

export const AuthRepository = {
  updateToken,

  signIn,
  renew,

  signUp,
  verifyEmail,

  sendResetPasswordEmail,
  resetPassword,
};
