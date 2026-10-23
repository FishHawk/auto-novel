import { useLocalStorage } from '@novelia/web-kit';
import { watch } from 'vue';

import { BannerApi, type Banner } from '@/api';

const STORAGE_KEY = 'auto-novel:banner:v1';
const CACHE_TTL = 12 * 60 * 60 * 1000;
const MANUAL_REFRESH_COOLDOWN = 60 * 1000;
const SELECTION_TTL = 2 * 60 * 60 * 1000;

interface BannerListCache {
  banners: Banner[];
  expiresAt: number;
}

export interface BannerSelection {
  banner: Banner;
  expiresAt: number;
}

interface StoredSelection {
  bannerId: number;
  expiresAt: number;
}

interface BannerCache {
  list?: BannerListCache;
  selection?: StoredSelection;
}

function hasExpiry(value: unknown): value is { expiresAt: number } {
  return (
    !!value &&
    typeof value === 'object' &&
    'expiresAt' in value &&
    typeof value.expiresAt === 'number' &&
    Number.isFinite(value.expiresAt)
  );
}

const state = useLocalStorage<BannerCache>(STORAGE_KEY, {
  fallback: () => ({}),
  validate: (value) => {
    if (!value || typeof value !== 'object' || Array.isArray(value))
      return undefined;
    const { list, selection } = value as BannerCache;
    if (
      list !== undefined &&
      (!hasExpiry(list) || !Array.isArray(list.banners))
    )
      return undefined;
    if (
      selection !== undefined &&
      (!hasExpiry(selection) || !Number.isSafeInteger(selection.bannerId))
    )
      return undefined;
    // 保留过期列表供后台刷新期间使用，旧选中 ID 用于继续轮换。
    return { list, selection };
  },
});
let pending: Promise<Banner[]> | undefined;
let lastRefreshAttempt = -Infinity;

export const BannerRepo = {
  /** 订阅列表替换（包括后台刷新和跨标签页更新），不监听选中项轮换。 */
  watchList: (callback: () => void) => watch(() => state.value.list, callback),

  /** 列表过期仍可首屏出图，选中项保持原来的轮换有效期。 */
  peek: (): BannerSelection | undefined => {
    const { list, selection } = state.value;
    if (!list || !selection) return undefined;
    const now = Date.now();
    if (now >= selection.expiresAt) return undefined;
    const banner = list.banners.find((it) => it.id === selection.bannerId);
    return banner ? { banner, expiresAt: selection.expiresAt } : undefined;
  },

  /** 过期列表先用旧缓存并后台刷新；手动切换等待刷新，失败也保留旧列表。 */
  list: (refresh = false): Promise<Banner[]> => {
    const cached = state.value.list;
    if (pending) {
      return !refresh && cached ? Promise.resolve(cached.banners) : pending;
    }
    const now = Date.now();
    // 从原缓存有效期推算成功拉取时间，兼容旧缓存并跨页面刷新保留冷却。
    const lastRefresh = Math.max(
      lastRefreshAttempt,
      cached ? cached.expiresAt - CACHE_TTL : -Infinity,
    );
    if (
      cached &&
      ((!refresh && now < cached.expiresAt) ||
        now < lastRefresh + MANUAL_REFRESH_COOLDOWN)
    ) {
      return Promise.resolve(cached.banners);
    }

    lastRefreshAttempt = now;
    pending = BannerApi.list()
      .then((banners) => {
        state.value.list = { banners, expiresAt: Date.now() + CACHE_TTL };
        return banners;
      })
      .catch((error: unknown) => {
        // 后台和手动刷新失败均保留旧列表，失败尝试也受冷却限制。
        if (cached) return cached.banners;
        throw error;
      })
      .finally(() => {
        pending = undefined;
      });
    return !refresh && cached ? Promise.resolve(cached.banners) : pending;
  },

  current: async (next = false): Promise<BannerSelection | undefined> => {
    const banners = await BannerRepo.list(next);
    if (banners.length === 0) {
      delete state.value.selection;
      return undefined;
    }

    const previous = state.value.selection;
    const index = banners.findIndex((it) => it.id === previous?.bannerId);
    if (!next && previous && index >= 0 && Date.now() < previous.expiresAt) {
      // 从列表读取最新数据，不延长原选中项的有效期。
      return { banner: banners[index], expiresAt: previous.expiresAt };
    }

    const banner = banners[(index + 1) % banners.length];
    const expiresAt = Date.now() + SELECTION_TTL;
    state.value.selection = { bannerId: banner.id, expiresAt };
    return { banner, expiresAt };
  },
};

/** 把焦点坐标换算成 object-position 百分比：cover 裁剪时让焦点尽量落在容器中心。 */
export function objectPosition(
  image: { width: number; height: number },
  box: { width: number; height: number },
  focal: { x: number; y: number },
): string {
  const { width: iw, height: ih } = image;
  const { width: bw, height: bh } = box;
  if (!iw || !ih || !bw || !bh) return '50% 50%';

  const scale = Math.max(bw / iw, bh / ih);
  const axis = (size: number, boxSize: number, focalValue: number) => {
    if (!Number.isFinite(focalValue)) return 50;
    const overflow = size * scale - boxSize;
    if (overflow <= 0) return 50;
    const ratio = (focalValue * size * scale - boxSize / 2) / overflow;
    return Math.min(1, Math.max(0, ratio)) * 100;
  };
  return `${axis(iw, bw, focal.x)}% ${axis(ih, bh, focal.y)}%`;
}
