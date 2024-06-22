import { UserRole } from '@/model/User';

export interface AuthProfile {
  id: string;
  email: string;
  username: string;
  role: UserRole;
  createAt: number;
}
