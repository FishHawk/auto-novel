import { validateImageType } from '../../api';

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
  validateImageType(file);
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

/** 文件大小：≥1 MiB 显示 MiB，否则显示 KiB。 */
export function formatFileSize(bytes: number) {
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(2)} MiB`
    : `${(bytes / 1024).toFixed(1)} KiB`;
}

export const defaultWebpQuality = 0.85;
const webpSourceTypes = new Set(['image/jpeg', 'image/png']);
let webpEncodingSupported: boolean | undefined;

/** 用 1×1 画布探测浏览器是否具备 WebP 编码能力（部分浏览器仅支持解码）。 */
export function supportsWebpCompression(): boolean {
  if (webpEncodingSupported !== undefined) return webpEncodingSupported;
  try {
    const canvas = document.createElement('canvas');
    canvas.width = 1;
    canvas.height = 1;
    webpEncodingSupported = canvas
      .toDataURL('image/webp')
      .startsWith('data:image/webp');
  } catch {
    webpEncodingSupported = false;
  }
  return webpEncodingSupported;
}

function webpFileName(name: string) {
  return /\.[^./\\]+$/.test(name)
    ? name.replace(/\.[^./\\]+$/, '.webp')
    : `${name}.webp`;
}

/** 以原始像素尺寸重绘并编码为 WebP；失败时返回 null。 */
function encodeWebp(image: BannerImage, quality: number): Promise<Blob | null> {
  return new Promise((resolve) => {
    const element = new Image();
    element.onload = () => {
      try {
        const canvas = document.createElement('canvas');
        canvas.width = element.naturalWidth;
        canvas.height = element.naturalHeight;
        const context = canvas.getContext('2d');
        if (!context) {
          resolve(null);
          return;
        }
        context.drawImage(element, 0, 0);
        canvas.toBlob(
          (blob) => resolve(blob?.type === 'image/webp' ? blob : null),
          'image/webp',
          quality,
        );
      } catch {
        resolve(null);
      }
    };
    element.onerror = () => resolve(null);
    element.src = image.url;
  });
}

/** 这张图是否具备压缩条件：浏览器支持 WebP 编码，且为 JPEG/PNG。 */
export function canCompressBannerImage(image: BannerImage): boolean {
  return webpSourceTypes.has(image.file.type) && supportsWebpCompression();
}

/**
 * 把图片压缩成 WebP，由界面按钮显式触发。
 * quality 取 0–1，越大越清晰、体积越大；不具备条件或没有变小时返回 null。
 */
export async function compressBannerImage(
  image: BannerImage,
  quality = defaultWebpQuality,
): Promise<File | null> {
  if (!canCompressBannerImage(image)) return null;
  const normalized = Math.min(1, Math.max(0, quality));
  const blob = await encodeWebp(image, normalized);
  if (!blob || blob.size === 0 || blob.size >= image.file.size) return null;
  return new File([blob], webpFileName(image.file.name), {
    type: 'image/webp',
  });
}
