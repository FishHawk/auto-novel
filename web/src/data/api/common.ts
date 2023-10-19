export interface Page<T> {
  pageNumber: number;
  items: T[];
}

export interface UserOutline {
  username: string;
  role: 'admin' | 'maintainer' | 'normal';
}
