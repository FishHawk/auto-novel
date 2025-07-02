import { useLocalStorage } from '@/util';

import { LSKey } from '../LocalStorage';

interface BlockUserComment {
  usernames: string[];
}

export const createBlockUserCommentRepository = () => {
  const ref = useLocalStorage<BlockUserComment>(LSKey.Blacklist, {
    usernames: [],
  });

  const add = (username: string) => {
    if (!ref.value.usernames.includes(username)) {
      ref.value.usernames.push(username);
    }
  };

  const remove = (username: string) => {
    ref.value.usernames = ref.value.usernames.filter(
      (name) => name !== username,
    );
  };

  return {
    ref,
    add,
    remove,
  };
};
