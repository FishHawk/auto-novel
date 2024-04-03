import { lazyUseLocalStorage } from '@/util/storage';

interface RuleViewed {
  wenkuUploadRule: number;
}

const lazyStorage = lazyUseLocalStorage<RuleViewed>('readState', {
  wenkuUploadRule: 0,
});

export const RuleViewedRepository = {
  ref: lazyStorage.ref,
};
