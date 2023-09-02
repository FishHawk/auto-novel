import { api } from './api';
import { Result, runCatching } from './result';

export interface SignInDto {
  email: string;
  username: string;
  role: 'normal' | 'admin' | 'maintainer';
  token: string;
  expiresAt: number;
}

const signIn = (emailOrUsername: string, password: string) =>
  runCatching(
    api
      .post(`auth/sign-in`, { json: { emailOrUsername, password } })
      .json<SignInDto>()
  );

const signUp = (
  email: string,
  emailCode: string,
  username: string,
  password: string
) =>
  runCatching(
    api
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
    api
      .post('auth/verify-email', {
        searchParams: { email },
      })
      .text()
  );

const sendResetPasswordEmail = (emailOrUsername: string) =>
  runCatching(
    api
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
    api
      .post('auth/reset-password', {
        searchParams: { emailOrUsername },
        json: { token, password },
      })
      .text()
  );

export const ApiAuth = {
  signIn,
  signUp,
  verifyEmail,
  sendResetPasswordEmail,
  resetPassword,
};
