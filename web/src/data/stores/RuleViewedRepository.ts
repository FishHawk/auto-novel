import { useLocalStorage } from '@vueuse/core';
import { LSKey } from '../LocalStorage';

interface RuleViewed {
  wenkuUploadRule: number;
}

export const createRuleViewedRepository = () => {
  const ref = useLocalStorage<RuleViewed>(LSKey.Notified, {
    wenkuUploadRule: 0,
  });
  return {
    ref,
  };
};
