import { runCatching } from '@/data/result';

import { client } from './client';

export interface SignInDto {
  username: string;
  role: 'normal' | 'admin' | 'maintainer';
  token: string;
  createAt: number;
  expiresAt: number;
}

const signIn = (emailOrUsername: string, password: string) =>
  runCatching(
    client
      .post(`auth/sign-in`, { json: { emailOrUsername, password } })
      .json<SignInDto>()
  );

const renew = () => runCatching(client.get(`auth/renew`).json<SignInDto>());

const signUp = (
  email: string,
  emailCode: string,
  username: string,
  password: string
) =>
  runCatching(
    client
      .post('auth/sign-up', {
        json: {
          email,
          emailCode,
          username,
          password,
        },
      })
      .json<SignInDto>()
  );

const verifyEmail = (email: string) =>
  runCatching(
    client
      .post('auth/verify-email', {
        searchParams: { email },
      })
      .text()
  );

const sendResetPasswordEmail = (emailOrUsername: string) =>
  runCatching(
    client
      .post('auth/reset-password-email', {
        searchParams: { emailOrUsername },
      })
      .text()
  );

const resetPassword = (
  emailOrUsername: string,
  token: string,
  password: string
) =>
  runCatching(
    client
      .post('auth/reset-password', {
        searchParams: { emailOrUsername },
        json: { token, password },
      })
      .text()
  );

export const ApiAuth = {
  signIn,
  renew,
  signUp,
  verifyEmail,
  sendResetPasswordEmail,
  resetPassword,
};
