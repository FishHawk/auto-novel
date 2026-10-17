import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  loadBannerImage,
  prepareBannerImage,
  validateDimensions,
  type BannerImage,
} from './bannerImage';

const createUrl = vi.spyOn(URL, 'createObjectURL');
const revokeUrl = vi.spyOn(URL, 'revokeObjectURL');
let decodeFails = false;
class FakeImage {
  naturalWidth = 1200;
  naturalHeight = 400;
  onload?: () => void;
  onerror?: () => void;
  set src(_value: string) {
    queueMicrotask(() => (decodeFails ? this.onerror?.() : this.onload?.()));
  }
}
const file = () => new File(['image'], 'banner.gif', { type: 'image/gif' });
function draft(overrides: Partial<BannerImage> = {}): BannerImage {
  return {
    file: file(),
    url: 'blob:preview',
    originalWidth: 1200,
    originalHeight: 400,
    width: 1200,
    height: 400,
    ...overrides,
  };
}
beforeEach(() => {
  vi.clearAllMocks();
  decodeFails = false;
  createUrl.mockReturnValue('blob:preview');
  vi.stubGlobal('Image', FakeImage);
});
afterEach(() => vi.unstubAllGlobals());

describe('本地 Banner 图片', () => {
  it('读取损坏图片失败会释放 URL', async () => {
    decodeFails = true;
    await expect(loadBannerImage(file())).rejects.toThrow('无法读取图片');
    expect(revokeUrl).toHaveBeenCalledWith('blob:preview');
  });
  it('原始尺寸直接保留文件及 GIF 动画，不进行重编码', async () => {
    const image = draft();
    expect(await prepareBannerImage(image)).toBe(image.file);
  });
  it('拒绝非法尺寸及超出像素上限的图片', () => {
    expect(() => validateDimensions(draft({ width: 0 }))).toThrow('宽高必须');
    expect(() =>
      validateDimensions(draft({ width: 8192, height: 8192 })),
    ).toThrow('宽高必须');
  });
  it('按指定宽高实际重绘并生成与扩展名一致的 PNG', async () => {
    const drawImage = vi.fn();
    const canvas = {
      width: 0,
      height: 0,
      getContext: vi.fn(() => ({ drawImage })),
      toBlob: vi.fn((callback: (blob: Blob) => void) =>
        callback(new Blob(['png'], { type: 'image/png' })),
      ),
    };
    vi.stubGlobal('document', { createElement: vi.fn(() => canvas) });
    const resized = await prepareBannerImage(
      draft({ width: 600, height: 200 }),
    );
    expect(canvas.width).toBe(600);
    expect(canvas.height).toBe(200);
    expect(drawImage).toHaveBeenCalledWith(
      expect.any(FakeImage),
      0,
      0,
      600,
      200,
    );
    expect(canvas.toBlob).toHaveBeenCalledWith(
      expect.any(Function),
      'image/png',
    );
    expect(resized.name).toBe('banner.png');
    expect(resized.type).toBe('image/png');
  });
  it('缩放结果超过上传限制时拒绝', async () => {
    vi.stubGlobal('document', {
      createElement: () => ({
        getContext: () => ({ drawImage: vi.fn() }),
        toBlob: (callback: (blob: Blob) => void) =>
          callback(
            new Blob([new Uint8Array(5 * 1024 * 1024 + 1)], {
              type: 'image/png',
            }),
          ),
      }),
    });
    await expect(prepareBannerImage(draft({ width: 600 }))).rejects.toThrow(
      '不超过 5 MiB',
    );
  });
});
