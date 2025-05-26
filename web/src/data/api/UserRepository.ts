import { Page } from '@/model/Page';
import { UserOutline, UserRole } from '@/model/User';

import { client } from './client';

const listUser = (params: { page: number; pageSize: number; role: UserRole }) =>
  client.get('user', { searchParams: params }).json<Page<UserOutline>>();

const updateRole = (json: { userId: string; role: UserRole }) =>
  client.put('user/role', { json });

export const UserRepository = {
  listUser,
  updateRole,
};
