import { useLocalStorage } from '@vueuse/core';

interface BlockUserComment {
  usernames: string[];
}

export const createBlockUserCommentRepository = () => {
  const ref = useLocalStorage<BlockUserComment>('blockComment', {
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
