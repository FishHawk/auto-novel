import { validLink } from '../../api';

export function validateBannerConfig(link: string, sortOrder: number): string {
  if (!validLink(link)) return '跳转地址必须为站内路径或 HTTP(S) 地址';
  if (
    !Number.isInteger(sortOrder) ||
    sortOrder < -2147483648 ||
    sortOrder > 2147483647
  ) {
    return '排序值必须为有效的 32 位整数';
  }
  return '';
}
