import { useLocalStorage } from '@novelia/web-kit';

import { BannerApi, type Banner } from '@/api';

const STORAGE_KEY = 'auto-novel:banner:v1';
const CACHE_TTL = 12 * 60 * 60 * 1000;
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
    // 过期只影响使用，不删除旧选中 ID，刷新列表后仍可选择下一张。
    return { list, selection };
  },
});
let pending: Promise<Banner[]> | undefined;

export const BannerRepo = {
  /** 同步读取仍有效的缓存选中项，供首屏直接出图，避免先闪一次占位。 */
  peek: (): BannerSelection | undefined => {
    const { list, selection } = state.value;
    if (!list || !selection) return undefined;
    const now = Date.now();
    if (now >= list.expiresAt || now >= selection.expiresAt) return undefined;
    const banner = list.banners.find((it) => it.id === selection.bannerId);
    return banner ? { banner, expiresAt: selection.expiresAt } : undefined;
  },

  list: (): Promise<Banner[]> => {
    const cached = state.value.list;
    if (cached && Date.now() < cached.expiresAt) {
      return Promise.resolve(cached.banners);
    }
    if (pending) return pending;

    pending = BannerApi.list()
      .then((banners) => {
        state.value.list = { banners, expiresAt: Date.now() + CACHE_TTL };
        return banners;
      })
      .finally(() => {
        pending = undefined;
      });
    return pending;
  },

  current: async (): Promise<BannerSelection | undefined> => {
    const banners = await BannerRepo.list();
    if (banners.length === 0) {
      delete state.value.selection;
      return undefined;
    }

    const previous = state.value.selection;
    const index = banners.findIndex((it) => it.id === previous?.bannerId);
    if (previous && index >= 0 && Date.now() < previous.expiresAt) {
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
