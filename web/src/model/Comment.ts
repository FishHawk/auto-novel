import { UserReference } from "./User";

export interface Comment1 {
  id: string;
  user: UserReference;
  content: string;
  createAt: number;
  numReplies: number;
  replies: Comment1[];
}
