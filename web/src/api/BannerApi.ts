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
    client.get('v1/banner/', { searchParams: { type: 0 } }).json<Banner[]>(),
};
