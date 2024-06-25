import { Page } from '@/model/Page';
import { UserOutline, UserRole } from '@/model/User';

import { client } from './client';

const listUser = (params: { page: number; pageSize: number; role: UserRole }) =>
  client.get('user', { searchParams: params }).json<Page<UserOutline>>();

export const UserRepository = {
  listUser,
};
