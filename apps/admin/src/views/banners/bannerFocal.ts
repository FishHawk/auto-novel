/** 默认焦点：居中，与后端和数据库的默认值一致。 */
export const defaultBannerFocal = 0.5;

// NaN 与 ±Infinity 不满足区间比较，无需额外判有限值。
export function validateBannerFocal(x: number, y: number): string {
  if (x >= 0 && x <= 1 && y >= 0 && y <= 1) return '';
  return '焦点坐标必须为 0–1 之间的有限数值';
}

/** cover 裁剪时让原图焦点尽量居中，受图片边缘限制，不露白。 */
export function bannerFocalPosition(
  imageWidth: number,
  imageHeight: number,
  width: number,
  height: number,
  x: number,
  y: number,
): string {
  if (
    imageWidth <= 0 ||
    imageHeight <= 0 ||
    width <= 0 ||
    height <= 0 ||
    validateBannerFocal(x, y)
  ) {
    return '50% 50%';
  }
  const scale = Math.max(width / imageWidth, height / imageHeight);
  const position = (image: number, container: number, focal: number) => {
    const overflow = image * scale - container;
    if (overflow <= 0) return 50;
    return (
      Math.min(
        1,
        Math.max(0, (focal * image * scale - container / 2) / overflow),
      ) * 100
    );
  };
  return `${position(imageWidth, width, x)}% ${position(imageHeight, height, y)}%`;
}
