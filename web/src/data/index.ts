import { createCachedSegRepository } from './CachedSegRepository';
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
  cachedSegRepository: lazyAsync(createCachedSegRepository),
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
  amazonRepository: lazy(createAmazonRepository),
  baiduRepository: lazy(createBaiduRepository),
  youdaoRepository: lazy(createYoudaoRepository),
  openAiRepositoryFactory: createOpenAiRepository,
  openAiWebRepositoryFactory: createOpenAiWebRepository,
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
