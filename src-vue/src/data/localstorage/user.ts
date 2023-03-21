export interface User {
  email: string;
  username: string;
  token: string;
  expiresAt: number;
}

const key = 'user';

export function getUser(): User | undefined {
  const raw = localStorage.getItem(key);
  if (raw) {
    try {
      const user: User = JSON.parse(raw);
      if (Date.now() / 1000 > user.expiresAt) {
        // token过期
        localStorage.removeItem(key);
        return undefined;
      } else {
        return user;
      }
    } catch (e) {
      localStorage.removeItem(key);
    }
  }
  return undefined;
}

export function setUser(user: User) {
  const parsed = JSON.stringify(user);
  localStorage.setItem(key, parsed);
}

export function deleteUser() {
  localStorage.removeItem(key);
}
