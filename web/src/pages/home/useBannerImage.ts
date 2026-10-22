import { useElementSize } from '@vueuse/core';

import type { Banner } from '@/api';
import { BannerRepo, objectPosition, type BannerSelection } from '@/repos';

const FallbackImage = '/files-extra/banner.v1.webp';

/** 首页横幅：首屏图片、焦点裁剪、兜底切换与定时轮换。 */
export function useBannerImage() {
  // 首屏同步出图：命中缓存则直接用缓存横幅，否则用兜底图，避免先闪一次占位色块。
  const initial = BannerRepo.peek();
  const banner = ref<Banner | undefined>(initial?.banner);
  const imageUrl = ref(initial?.banner.imageUrl || FallbackImage);
  const container = ref<HTMLElement>();
  const { width, height } = useElementSize(container);
  const imageSize = ref({ width: 0, height: 0 });
  const loading = ref(true);
  const switching = ref(false);
  const canSwitch = ref(false);
  const switchError = ref('');

  const imagePosition = computed(() => {
    // 无横幅数据时沿用旧版 bg-top 的对齐方式。
    if (!banner.value) return '50% 0%';
    return objectPosition(
      imageSize.value,
      { width: width.value, height: height.value },
      { x: banner.value.focalX, y: banner.value.focalY },
    );
  });

  const imageLoaded = (event: Event) => {
    const image = event.target as HTMLImageElement;
    imageSize.value = {
      width: image.naturalWidth,
      height: image.naturalHeight,
    };
    loading.value = false;
  };

  const showFallback = () => {
    banner.value = undefined;
    if (imageUrl.value === FallbackImage) {
      // 兜底图也不可用时结束加载，保留主题色背景。
      imageUrl.value = '';
      loading.value = false;
      return;
    }
    loading.value = true;
    imageSize.value = { width: 0, height: 0 };
    imageUrl.value = FallbackImage;
  };

  let rotationTimer: ReturnType<typeof setTimeout> | undefined;
  let disposed = false;
  let reloadPending = false;
  const loadBanner = async (next = false) => {
    if (switching.value || disposed) return;
    switching.value = true;
    switchError.value = '';
    clearTimeout(rotationTimer);
    let selected: BannerSelection | undefined;
    try {
      selected = await BannerRepo.current(next);
      if (disposed) return;
      const banners = await BannerRepo.list();
      if (disposed) return;
      canSwitch.value = banners.length > 1;
      if (!selected?.banner.imageUrl) {
        if (!next && imageUrl.value !== FallbackImage) showFallback();
        return;
      }

      // 手动切换先预加载，失败时不清空当前背景。
      if (next && imageUrl.value !== selected.banner.imageUrl) {
        const image = new Image();
        const url = selected.banner.imageUrl;
        await new Promise<void>((resolve, reject) => {
          const finish = (error?: Error) => {
            clearTimeout(timeout);
            image.onload = null;
            image.onerror = null;
            if (error) reject(error);
            else resolve();
          };
          const timeout = setTimeout(
            () => finish(new Error('图片加载超时')),
            15000,
          );
          image.onload = () => finish();
          image.onerror = () => finish(new Error('图片加载失败'));
          image.src = url;
        });
        if (disposed) return;
        imageSize.value = {
          width: image.naturalWidth,
          height: image.naturalHeight,
        };
      }
      banner.value = selected.banner;
      if (imageUrl.value !== selected.banner.imageUrl) {
        loading.value = !next;
        if (!next) imageSize.value = { width: 0, height: 0 };
        imageUrl.value = selected.banner.imageUrl;
      }
    } catch {
      if (disposed) return;
      if (next) {
        switchError.value = '切换失败，点击重试';
        // 预加载失败时保留旧背景，不能让本次刷新通知绕过预加载再次应用失败图片。
        reloadPending = false;
      } else showFallback();
    } finally {
      switching.value = false;
      if (!disposed && selected) {
        rotationTimer = setTimeout(
          () => void loadBanner(),
          Math.max(0, selected.expiresAt - Date.now()),
        );
      }
      // 刷新可能与当前加载/手动预加载重叠，结束后再协调最新列表。
      if (!disposed && reloadPending) {
        reloadPending = false;
        void loadBanner();
      }
    }
  };
  const stopWatchingList = BannerRepo.watchList(() => {
    if (switching.value) reloadPending = true;
    else void loadBanner();
  });
  const nextBanner = () => loadBanner(true);
  onMounted(() => void loadBanner());
  onBeforeUnmount(() => {
    disposed = true;
    stopWatchingList();
    clearTimeout(rotationTimer);
  });

  return {
    container,
    imageUrl,
    imagePosition,
    loading,
    switching,
    canSwitch,
    switchError,
    nextBanner,
    imageLoaded,
    showFallback,
  };
}
