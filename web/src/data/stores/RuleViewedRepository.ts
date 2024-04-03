import { useLocalStorage } from '@vueuse/core';

interface RuleViewed {
  wenkuUploadRule: number;
}

export const createRuleViewedRepository = () => {
  const ref = useLocalStorage<RuleViewed>('readState', {
    wenkuUploadRule: 0,
  });
  return {
    ref,
  };
};
