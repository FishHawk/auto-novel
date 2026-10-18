import { validateImage } from '../../api';

export interface BannerImage {
  file: File;
  url: string;
  width: number;
  height: number;
}

function decodeImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () =>
      reject(new Error('无法读取图片，请选择有效的图片文件'));
    image.src = url;
  });
}

export async function loadBannerImage(file: File): Promise<BannerImage> {
  validateImage(file);
  const url = URL.createObjectURL(file);
  try {
    const image = await decodeImage(url);
    const width = image.naturalWidth;
    const height = image.naturalHeight;
    if (
      !Number.isInteger(width) ||
      !Number.isInteger(height) ||
      width < 1 ||
      height < 1 ||
      width > 8192 ||
      height > 8192 ||
      width * height > 32 * 1024 * 1024
    ) {
      throw new Error('宽高必须为 1–8192 的整数，且总像素不能超过 32 Mi');
    }
    return { file, url, width, height };
  } catch (error) {
    URL.revokeObjectURL(url);
    throw error;
  }
}
