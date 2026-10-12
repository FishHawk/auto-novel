import { webKit } from '@/webKit';
import { LSKey } from './key';

export const useWhoamiStore = defineStore(LSKey.Auth, () => {
  const whoami = computed(() => {
    const session = webKit.whoami.value;
    const atLeastMember = session.hasRoleAtLeast('member');
    const oldEnough = session.isAtLeastDaysOld(30);
    return {
      ...session,
      hasNsfwAccess: session.hasRoleAtLeast('restricted') && oldEnough,
      hasForumAccess: atLeastMember,
      hasNovelAccess: atLeastMember && oldEnough,
      isMe: (username: string) => session.user?.username === username,
    };
  });

  return { whoami, logout: webKit.logout };
});
