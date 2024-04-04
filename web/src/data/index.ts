import { AmazonNovelRepository } from './amazon';
import {
  ArticleRepository,
  AuthRepository,
  CommentRepository,
  OperationRepository,
  SakuraRepository,
  UserRepository,
  WebNovelRepository,
  WenkuNovelRepository,
} from './api';
import { createLocalVolumeRepository } from './local';
import {
  createGptWorkspaceRepository,
  createReadPositionRepository,
  createReaderSettingRepository,
  createRuleViewedRepository,
  createSakuraWorkspaceRepository,
  createSettingRepository,
  createUserDataRepository,
  createWebSearchHistoryRepository,
  createWenkuSearchHistoryRepository,
} from './stores';
import { createCachedSegRepository } from './translator';

export { formatError } from './api';

const lazy = <T>(factory: () => T) => {
  let value: T;
  const get = () => {
    if (value === undefined) {
      value = factory();
    }
    return value;
  };
  return get;
};

const lazyAsync = <T>(factory: () => Promise<T>) => {
  let value: Promise<T>;
  const get = async () => {
    if (value === undefined) {
      value = factory();
    }
    return await value;
  };
  return get;
};

export const Locator = {
  localVolumeRepository: lazyAsync(createLocalVolumeRepository),
  //
  ruleViewedRepository: lazy(createRuleViewedRepository),
  readPositionRepository: lazy(createReadPositionRepository),
  settingRepository: lazy(createSettingRepository),
  readerSettingRepository: lazy(createReaderSettingRepository),
  webSearchHistoryRepository: lazy(createWebSearchHistoryRepository),
  wenkuSearchHistoryRepository: lazy(createWenkuSearchHistoryRepository),
  gptWorkspaceRepository: lazy(createGptWorkspaceRepository),
  sakuraWorkspaceRepository: lazy(createSakuraWorkspaceRepository),
  userDataRepository: lazy(createUserDataRepository),
  //
  cachedSegRepository: lazyAsync(createCachedSegRepository),
  //
  amazonNovelRepository: AmazonNovelRepository,
  //
  articleRepository: ArticleRepository,
  authRepository: AuthRepository,
  commentRepository: CommentRepository,
  operationRepository: OperationRepository,
  sakuraRepository: SakuraRepository,
  userRepository: UserRepository,
  webNovelRepository: WebNovelRepository,
  wenkuNovelRepository: WenkuNovelRepository,
};
