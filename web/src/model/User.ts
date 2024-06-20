export type UserRole = 'admin' | 'maintainer' | 'trusted' | 'normal' | 'banned';

export interface UserReference {
  username: string;
  role: UserRole;
}

export interface UserOutline {
  id: string;
  email: string;
  username: boolean;
  role: UserRole;
  createAt: number;
}

export interface Favored {
  id: string;
  title: string;
}
export interface FavoredList {
  favoredWeb: Favored[];
  favoredWenku: Favored[];
}
