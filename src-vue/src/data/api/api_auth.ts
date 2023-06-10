import api from './api';
import { Result, runCatching } from './result';

export interface SignInDto {
  email: string;
  username: string;
  role: 'normal' | 'admin' | 'maintainer';
  token: string;
  expiresAt: number;
}

async function signIn(
  emailOrUsername: string,
  password: string
): Promise<Result<SignInDto>> {
  return runCatching(
    api.post(`auth/sign-in`, { json: { emailOrUsername, password } }).json()
  );
}

async function signUp(
  email: string,
  emailCode: string,
  username: string,
  password: string
): Promise<Result<SignInDto>> {
  return runCatching(
    api
      .post('auth/sign-up', {
        json: {
          email,
          emailCode,
          username,
          password,
        },
      })
      .json()
  );
}

async function verifyEmail(email: string): Promise<Result<string>> {
  return runCatching(
    api
      .post('auth/verify-email', {
        searchParams: { email },
      })
      .text()
  );
}

async function sendResetPasswordEmail(
  emailOrUsername: string
): Promise<Result<string>> {
  return runCatching(
    api
      .post('auth/reset-password-email', {
        searchParams: { emailOrUsername },
      })
      .text()
  );
}

async function resetPassword(
  emailOrUsername: string,
  token: string,
  password: string
): Promise<Result<string>> {
  return runCatching(
    api
      .post('auth/reset-password', {
        searchParams: { emailOrUsername },
        json: { token, password },
      })
      .text()
  );
}

export const ApiAuth = {
  signIn,
  signUp,
  verifyEmail,
  sendResetPasswordEmail,
  resetPassword,
};
