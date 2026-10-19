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
  const loadBanner = async () => {
    clearTimeout(rotationTimer);
    const selected: BannerSelection | undefined =
      await BannerRepo.current().catch(() => undefined);
    if (disposed) return;
    if (!selected?.banner.imageUrl) {
      showFallback();
      return;
    }

    banner.value = selected.banner;
    rotationTimer = setTimeout(
      loadBanner,
      Math.max(0, selected.expiresAt - Date.now()),
    );
    if (imageUrl.value !== selected.banner.imageUrl) {
      loading.value = true;
      imageSize.value = { width: 0, height: 0 };
      imageUrl.value = selected.banner.imageUrl;
    }
  };
  onMounted(loadBanner);
  onBeforeUnmount(() => {
    disposed = true;
    clearTimeout(rotationTimer);
  });

  return {
    container,
    imageUrl,
    imagePosition,
    loading,
    imageLoaded,
    showFallback,
  };
}
