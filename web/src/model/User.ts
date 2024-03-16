export type UserRole = 'admin' | 'maintainer' | 'trusted' | 'normal' | 'banned';

export interface UserProfile {
  username: string;
  role: UserRole;
  token: string;
  createAt: number;
  expiresAt: number;
}

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
  web: Favored[];
  wenku: Favored[];
}
