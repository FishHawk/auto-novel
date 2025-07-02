import { createGlobalState } from '@vueuse/core';
import { createCachedSegRepository } from './CachedSegRepository';
import {
  ArticleRepository,
  CommentRepository,
  OperationRepository,
  UserRepository,
  WebNovelRepository,
  WenkuNovelRepository,
} from './api';
import { createAuthRepository } from './auth/AuthRepository';
import { createFavoredRepository } from './favored/FavoredRepository';
import { createLocalVolumeRepository } from './local';
import { createReadHistoryRepository } from './read-history/ReadHistoryRepository';
import {
  createReaderSettingRepository,
  createSettingRepository,
} from './setting/SettingRepository';
import {
  createDraftRepository,
  createGptWorkspaceRepository,
  createReadPositionRepository,
  createRuleViewedRepository,
  createSakuraWorkspaceRepository,
  createWebSearchHistoryRepository,
  createWenkuSearchHistoryRepository,
  createBlockUserCommentRepository,
} from './stores';
import {
  createAmazonRepository,
  createBaiduRepository,
  createOpenAiRepository,
  createOpenAiWebRepository,
  createYoudaoRepository,
} from './third-party';
export { OpenAiError } from './third-party/OpenAiRepository';

export { formatError } from './api';

const lazy = <T>(factory: () => T) => {
  let value: T;
  const get = () => {
    if (value === undefined) {
      value = createGlobalState(factory)();
    }
    return value;
  };
  return get;
};

const lazyAsync = <T>(factory: () => Promise<T>) => {
  let value: Promise<T>;
  const get = async () => {
    if (value === undefined) {
      value = createGlobalState(factory)();
    }
    return await value;
  };
  return get;
};

export const Locator = {
  localVolumeRepository: lazyAsync(createLocalVolumeRepository),
  //
  cachedSegRepository: lazyAsync(createCachedSegRepository),
  //
  draftRepository: lazy(createDraftRepository),
  ruleViewedRepository: lazy(createRuleViewedRepository),
  readPositionRepository: lazy(createReadPositionRepository),
  settingRepository: lazy(createSettingRepository),
  readerSettingRepository: lazy(createReaderSettingRepository),
  webSearchHistoryRepository: lazy(createWebSearchHistoryRepository),
  wenkuSearchHistoryRepository: lazy(createWenkuSearchHistoryRepository),
  gptWorkspaceRepository: lazy(createGptWorkspaceRepository),
  sakuraWorkspaceRepository: lazy(createSakuraWorkspaceRepository),
  //
  amazonRepository: lazy(createAmazonRepository),
  baiduRepository: lazy(createBaiduRepository),
  youdaoRepository: lazy(createYoudaoRepository),
  openAiRepositoryFactory: createOpenAiRepository,
  openAiWebRepositoryFactory: createOpenAiWebRepository,
  //
  articleRepository: ArticleRepository,
  commentRepository: CommentRepository,
  operationRepository: OperationRepository,
  userRepository: UserRepository,
  webNovelRepository: WebNovelRepository,
  wenkuNovelRepository: WenkuNovelRepository,
  //
  authRepository: lazy(createAuthRepository),
  favoredRepository: lazy(createFavoredRepository),
  readHistoryRepository: lazy(createReadHistoryRepository),
  blockUserCommentRepository: lazy(createBlockUserCommentRepository),
};
