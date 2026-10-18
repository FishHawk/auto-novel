<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import BannerPreview from './BannerPreview.vue';
import { XDropZone, getApiErrorMessage } from '@novelia/web-kit';
import { imageAccept, useBannerApi, validateImage } from '../../api';
import {
  canCompressBannerImage,
  compressBannerImage,
  defaultWebpQuality,
  loadBannerImage,
  type BannerImage,
} from './bannerImage';
import { validateBannerConfig } from './bannerConfig';
import BannerImageInfo from './BannerImageInfo.vue';
import WebpCompressionPanel from './WebpCompressionPanel.vue';

const props = defineProps<{ disabled?: boolean }>();
const emit = defineEmits<{ saved: []; busy: [value: boolean] }>();
const api = useBannerApi();
interface PendingBanner {
  id: number;
  image: BannerImage;
  link: string;
  sortOrder: number;
  uploaded?: { key: string; imageUrl: string };
  compressed?: File;
  compressedUrl?: string;
  error: string;
  status: string;
}
const files = ref<PendingBanner[]>([]);
const selectedId = ref<number>();
const selected = computed(
  () =>
    files.value.find((item) => item.id === selectedId.value) ?? files.value[0],
);
const selectedCompressible = computed(() => {
  const item = selected.value;
  return !!item && !item.uploaded && canCompressBannerImage(item.image);
});
const input = ref<HTMLInputElement>();
const loading = ref(false);
const uploading = ref(false);
const compressing = ref(false);
const webpQuality = ref(defaultWebpQuality);
const error = ref('');
const notice = ref('');
let nextId = 0;
let disposed = false;
const controller = new AbortController();

async function add(selected: File[]) {
  if (
    props.disabled ||
    disposed ||
    uploading.value ||
    loading.value ||
    compressing.value
  )
    return;
  loading.value = true;
  error.value = '';
  notice.value = '';
  const errors: string[] = [];
  try {
    for (const file of selected) {
      try {
        const image = await loadBannerImage(file);
        if (disposed) {
          URL.revokeObjectURL(image.url);
          break;
        }
        files.value.push({
          id: nextId++,
          image,
          link: '',
          sortOrder: 0,
          error: '',
          status: '待上传',
        });
      } catch (cause) {
        errors.push(`${file.name}：${getApiErrorMessage(cause, '读取失败')}`);
      }
    }
    error.value = errors.join('；');
  } finally {
    loading.value = false;
  }
}
function select(event: Event) {
  const target = event.target as HTMLInputElement;
  void add(Array.from(target.files ?? []));
  target.value = '';
}
function remove(item: PendingBanner) {
  URL.revokeObjectURL(item.image.url);
  if (item.compressedUrl) URL.revokeObjectURL(item.compressedUrl);
  files.value = files.value.filter((value) => value.id !== item.id);
}
function useOriginal(item: PendingBanner) {
  if (compressing.value) return;
  if (item.compressedUrl) URL.revokeObjectURL(item.compressedUrl);
  item.compressed = undefined;
  item.compressedUrl = undefined;
  item.status = '待上传';
  item.error = '';
}
async function compressSelected() {
  const item = selected.value;
  if (
    !item ||
    item.uploaded ||
    props.disabled ||
    disposed ||
    loading.value ||
    uploading.value ||
    compressing.value
  )
    return;
  error.value = '';
  notice.value = '';
  item.error = '';
  item.status = '正在压缩…';
  compressing.value = true;
  const quality = webpQuality.value;
  try {
    const file = await compressBannerImage(item.image, quality);
    if (!file) {
      item.status = item.compressed
        ? '重新压缩未变小，保留上次结果'
        : '未压缩（压缩失败或没有变小）';
      return;
    }
    item.compressed = file;
    if (item.compressedUrl) URL.revokeObjectURL(item.compressedUrl);
    item.compressedUrl = URL.createObjectURL(file);
    item.status = '待上传';
  } catch (cause) {
    item.status = '压缩失败';
    item.error = getApiErrorMessage(cause, '压缩失败');
  } finally {
    compressing.value = false;
  }
}
async function upload() {
  if (
    props.disabled ||
    disposed ||
    uploading.value ||
    loading.value ||
    compressing.value ||
    !files.value.length
  )
    return;
  error.value = '';
  notice.value = '';
  // 先检查整批配置，避免输入错误导致部分上传。
  for (const item of files.value) {
    const message = validateBannerConfig(item.link, item.sortOrder);
    if (message) {
      selectedId.value = item.id;
      error.value = `${item.image.file.name}：${message}`;
      return;
    }
  }
  uploading.value = true;
  emit('busy', true);
  let saved = 0;
  try {
    // 校验整批文件，任何一张不通过都不上传。
    const invalid: PendingBanner[] = [];
    for (const item of [...files.value]) {
      if (disposed) return;
      item.error = '';
      if (item.uploaded) continue;
      try {
        validateImage(item.compressed ?? item.image.file);
      } catch (cause) {
        item.status = '未通过校验';
        item.error = getApiErrorMessage(cause, '校验失败');
        invalid.push(item);
      }
    }
    if (invalid.length) {
      selectedId.value = invalid[0]!.id;
      error.value = invalid
        .map((item) => `${item.image.file.name}：${item.error}`)
        .join('；');
      return;
    }
    for (const item of [...files.value]) {
      if (disposed) break;
      item.error = '';
      try {
        if (!item.uploaded) {
          item.status = '正在上传…';
          item.uploaded = await api.upload(item.compressed ?? item.image.file, {
            signal: controller.signal,
            onProgress: ({ loaded, total }) => {
              item.status = total
                ? `上传 ${Math.floor((loaded / total) * 100)}%`
                : '正在上传…';
            },
          });
        }
        if (disposed) break;
        item.status = '保存 Banner…';
        await api.save(undefined, {
          type: 0,
          key: item.uploaded.key,
          linkUrl: item.link.trim() || null,
          sortOrder: item.sortOrder,
        });
        remove(item);
        saved++;
      } catch (cause) {
        item.status = '失败，可重试';
        item.error = getApiErrorMessage(cause, '上传失败');
      }
    }
  } finally {
    uploading.value = false;
    emit('busy', false);
    if (saved) {
      notice.value = `已上传 ${saved} 张 Banner`;
      emit('saved');
    }
  }
}
onBeforeUnmount(() => {
  disposed = true;
  controller.abort();
  for (const item of files.value) {
    URL.revokeObjectURL(item.image.url);
    if (item.compressedUrl) URL.revokeObjectURL(item.compressedUrl);
  }
});
</script>

<template>
  <XDropZone
    :disabled="disabled || loading || uploading || compressing"
    @drop="add"
  >
    松开以加载 Banner 图片（不会立即上传）
  </XDropZone>
  <section
    class="banner-panel upload-panel"
    aria-label="加载和上传 Banner"
    :aria-busy="uploading || compressing"
  >
    <input
      ref="input"
      type="file"
      :accept="imageAccept"
      :disabled="disabled || loading || uploading || compressing"
      multiple
      hidden
      @change="select"
    />
    <div v-if="!files.length" class="upload-empty">
      <button
        type="button"
        class="btn"
        :disabled="disabled || loading || uploading || compressing"
        @click="input?.click()"
      >
        {{ loading ? '读取中…' : '加载图片' }}
      </button>
      <p class="hint">或拖入图片，支持多张；加载仅在本地进行，不会立即上传。</p>
      <p class="hint">JPEG / PNG / WebP / GIF / AVIF；加载不限大小。</p>
      <p class="hint">
        上传时校验最终文件 ≤ 5 MiB；JPEG / PNG 选中后可先压缩为
        WebP（需浏览器支持）。
      </p>
    </div>
    <BannerPreview
      v-if="selected"
      :src="selected.compressedUrl ?? selected.image.url"
      :alt="selected.image.file.name"
    >
      <template #actions>
        <button
          type="button"
          class="btn"
          :disabled="disabled || loading || uploading || compressing"
          @click="input?.click()"
        >
          {{ loading ? '读取中…' : '继续添加' }}
        </button>
      </template>
    </BannerPreview>
    <div
      v-if="files.length"
      class="banner-thumbnails"
      aria-label="待上传原图列表"
    >
      <button
        v-for="item in files"
        :key="item.id"
        type="button"
        class="banner-thumbnail"
        :class="{
          selected: selected?.id === item.id,
          'has-error': !!item.error,
        }"
        :aria-pressed="selected?.id === item.id"
        :aria-label="`${item.image.file.name}，${item.status}`"
        :title="`${item.image.file.name} · ${item.status}`"
        :disabled="compressing"
        @click="selectedId = item.id"
      >
        <img
          :src="item.compressedUrl ?? item.image.url"
          :alt="item.image.file.name"
        />
      </button>
    </div>
    <form class="banner-form upload-form" @submit.prevent="upload">
      <fieldset :disabled="disabled || uploading || compressing">
        <article v-if="selected" :key="selected.id" class="pending-banner">
          <div class="pending-heading">
            <strong>{{ selected.image.file.name }}</strong>
            <button
              type="button"
              class="text-button danger"
              @click="remove(selected)"
            >
              移除
            </button>
          </div>
          <BannerImageInfo v-if="!selected.uploaded" :image="selected.image" />
          <WebpCompressionPanel
            v-if="selectedCompressible"
            v-model="webpQuality"
            :original-size="selected.image.file.size"
            :compressed="selected.compressed"
            :disabled="disabled || loading || uploading"
            :busy="compressing"
            @compress="compressSelected"
            @revert="useOriginal(selected)"
          />
          <p v-if="selected.uploaded" class="hint">
            图片已上传，重试仅保存配置，不会重复上传。
          </p>
          <div class="form-grid">
            <label class="field">
              <span>跳转地址（可选）</span>
              <input
                v-model="selected.link"
                placeholder="/novel 或 https://example.com"
              />
            </label>
            <label class="field">
              <span>排序值</span>
              <input
                v-model.number="selected.sortOrder"
                type="number"
                min="-2147483648"
                max="2147483647"
                step="1"
                required
              />
            </label>
          </div>
          <p v-if="selected.status !== '待上传'" role="status" class="hint">
            {{ selected.status }}
          </p>
          <p v-if="selected.error" class="error-notice" role="alert">
            {{ selected.error }}
          </p>
        </article>
      </fieldset>
      <p v-if="error" class="error-notice" role="alert">{{ error }}</p>
      <p v-if="notice" class="success-notice" role="status">{{ notice }}</p>
      <div v-if="files.length" class="form-actions">
        <span class="hint">{{ files.length }} 张待上传 · 新建后默认启用</span>
        <button
          class="btn btn-primary"
          type="submit"
          :disabled="disabled || loading || uploading || compressing"
        >
          {{ uploading ? '正在上传…' : '上传' }}
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped src="./banner.shared.css"></style>

<style scoped>
.upload-panel {
  margin-bottom: 28px;
}
.upload-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 32px 20px;
  text-align: center;
}
.upload-empty .hint {
  margin: 0;
}
.upload-form {
  padding: 0 20px 20px;
}
.pending-banner {
  padding: 20px 0;
}
.pending-heading {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
  overflow-wrap: anywhere;
}
.pending-heading strong {
  min-width: 0;
}
.pending-heading button {
  flex-shrink: 0;
}
</style>
