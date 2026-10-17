import { validateImage } from '../../api';

export interface BannerImage {
  file: File;
  url: string;
  originalWidth: number;
  originalHeight: number;
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
    if (!image.naturalWidth || !image.naturalHeight)
      throw new Error('图片尺寸无效');
    return {
      file,
      url,
      originalWidth: image.naturalWidth,
      originalHeight: image.naturalHeight,
      width: image.naturalWidth,
      height: image.naturalHeight,
    };
  } catch (error) {
    URL.revokeObjectURL(url);
    throw error;
  }
}

export function validateDimensions(image: BannerImage) {
  if (
    !Number.isInteger(image.width) ||
    !Number.isInteger(image.height) ||
    image.width < 1 ||
    image.height < 1 ||
    image.width > 8192 ||
    image.height > 8192 ||
    image.width * image.height > 32 * 1024 * 1024
  ) {
    throw new Error('宽高必须为 1–8192 的整数，且总像素不能超过 32 Mi');
  }
}

export async function prepareBannerImage(image: BannerImage): Promise<File> {
  validateDimensions(image);
  if (
    image.width === image.originalWidth &&
    image.height === image.originalHeight
  )
    return image.file;
  const source = await decodeImage(image.url);
  const canvas = document.createElement('canvas');
  canvas.width = image.width;
  canvas.height = image.height;
  const context = canvas.getContext('2d');
  if (!context) throw new Error('当前浏览器不支持图片缩放');
  context.drawImage(source, 0, 0, image.width, image.height);
  // 使用所有目标浏览器均支持的 PNG，避免 AVIF/GIF 编码回退及类型不一致。
  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (value) => (value ? resolve(value) : reject(new Error('图片缩放失败'))),
      'image/png',
    );
  });
  const file = new File(
    [blob],
    `${image.file.name.replace(/\.[^.]+$/, '')}.png`,
    { type: 'image/png' },
  );
  validateImage(file);
  return file;
}
