import { client } from './client';

export interface Banner {
  id: number;
  type: number;
  imageUrl: string;
  linkUrl: string | null;
  focalX: number;
  focalY: number;
  sortOrder: number;
}

export const BannerApi = {
  list: () =>
    client
      .get('v1/banner/', {
        searchParams: { type: 0 },
        // 横幅属装饰内容：超时短、失败不重试，尽快回落到兜底图。
        timeout: 5_000,
        retry: 0,
      })
      .json<Banner[]>(),
};
