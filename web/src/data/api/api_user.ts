import { runCatching } from '@/data/result';

import { client } from './client';
import { Page } from './common';

export type UserRole = 'admin' | 'maintainer' | 'normal' | 'banned';

export interface UserReference {
  username: string;
  role: UserRole;
}

export interface UserOutline {
  id: string;
  email: string;
  username: boolean;
  role: string;
  createAt: number;
}

const listUser = (params: { page: number; pageSize: number; role: UserRole }) =>
  runCatching(
    client.get('user', { searchParams: params }).json<Page<UserOutline>>()
  );

export const ApiUser = {
  listUser,
};
