<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { XDialog, XDropZone, getApiErrorMessage } from '@novelia/web-kit';
import {
  canCompressBannerImage,
  compressBannerImage,
  defaultWebpQuality,
  loadBannerImage,
  type BannerImage,
} from './bannerImage';
import { validateBannerConfig } from './bannerConfig';
import BannerImageInfo from './BannerImageInfo.vue';
import BannerPreview from './BannerPreview.vue';
import WebpCompressionPanel from './WebpCompressionPanel.vue';
import {
  imageAccept,
  useBannerApi,
  validateImage,
  type Banner,
} from '../../api';

const props = defineProps<{ banner: Banner }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const api = useBannerApi();
const link = ref(props.banner.linkUrl ?? '');
const sortOrder = ref(props.banner.sortOrder);
const uploading = ref(false);
const saving = ref(false);
const compressing = ref(false);
const webpQuality = ref(defaultWebpQuality);
const error = ref('');
const note = ref('');
const image = ref<BannerImage>();
const fileInput = ref<HTMLInputElement>();
const compressed = ref<File>();
const compressedUrl = ref<string>();
const uploaded = ref<{ key: string; imageUrl: string }>();
const controller = new AbortController();
let disposed = false;
const canCompress = computed(
  () => !uploaded.value && !!image.value && canCompressBannerImage(image.value),
);

async function selectFiles(files: File[]) {
  if (saving.value || uploading.value || compressing.value || !files.length)
    return;
  if (files.length !== 1) {
    error.value = '编辑时请只加载一张替换图片';
    return;
  }
  error.value = '';
  note.value = '';
  uploading.value = true;
  try {
    const next = await loadBannerImage(files[0]!);
    if (disposed) {
      URL.revokeObjectURL(next.url);
      return;
    }
    if (image.value) URL.revokeObjectURL(image.value.url);
    if (compressedUrl.value) URL.revokeObjectURL(compressedUrl.value);
    image.value = next;
    compressed.value = undefined;
    compressedUrl.value = undefined;
    uploaded.value = undefined;
  } catch (cause) {
    error.value = getApiErrorMessage(cause, '读取图片失败');
  } finally {
    uploading.value = false;
  }
}
function selectImage(event: Event) {
  const input = event.target as HTMLInputElement;
  void selectFiles(Array.from(input.files ?? []));
  input.value = '';
}
onBeforeUnmount(() => {
  disposed = true;
  controller.abort();
  if (image.value) URL.revokeObjectURL(image.value.url);
  if (compressedUrl.value) URL.revokeObjectURL(compressedUrl.value);
});

async function compressImage() {
  if (
    !image.value ||
    uploaded.value ||
    compressing.value ||
    uploading.value ||
    saving.value
  )
    return;
  error.value = '';
  note.value = '';
  compressing.value = true;
  const quality = webpQuality.value;
  try {
    const file = await compressBannerImage(image.value, quality);
    if (!file) {
      note.value = compressed.value
        ? '重新压缩未变小，保留上次结果'
        : '压缩失败或没有变小，继续使用原图';
      return;
    }
    compressed.value = file;
    if (compressedUrl.value) URL.revokeObjectURL(compressedUrl.value);
    compressedUrl.value = URL.createObjectURL(file);
  } catch (cause) {
    error.value = getApiErrorMessage(cause, '压缩失败');
  } finally {
    compressing.value = false;
  }
}
function useOriginal() {
  if (compressing.value || uploaded.value) return;
  if (compressedUrl.value) URL.revokeObjectURL(compressedUrl.value);
  compressed.value = undefined;
  compressedUrl.value = undefined;
  note.value = '';
}

async function save() {
  if (saving.value || uploading.value || compressing.value) return;
  error.value = validateBannerConfig(link.value, sortOrder.value);
  if (error.value) return;
  saving.value = true;
  try {
    if (image.value && !uploaded.value) {
      const file = compressed.value ?? image.value.file;
      validateImage(file);
      uploaded.value = await api.upload(file, {
        signal: controller.signal,
      });
    }
    if (disposed) return;
    await api.save(props.banner.id, {
      type: 0,
      key: uploaded.value?.key ?? props.banner.key,
      linkUrl: link.value.trim() || null,
      sortOrder: sortOrder.value,
    });
    emit('saved');
  } catch (cause) {
    error.value = getApiErrorMessage(cause, '操作失败');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <XDropZone :disabled="uploading || saving || compressing" @drop="selectFiles">
    松开以加载替换图片（不会立即上传）
  </XDropZone>
  <XDialog
    :open="true"
    :title="`编辑 Banner #${banner.id}`"
    description="管理首页轮播图片、跳转地址和展示顺序。"
    :closable="!uploading && !saving && !compressing"
    @update:open="!$event && emit('close')"
  >
    <form class="banner-form" @submit.prevent="save">
      <fieldset :disabled="uploading || saving || compressing">
        <BannerPreview
          :src="compressedUrl || image?.url || banner.imageUrl"
          :alt="image?.file.name || 'Banner 图片预览'"
        />
        <div class="field image-field">
          <span>
            图片
            <span class="required">*</span>
          </span>
          <div class="image-picker">
            <input
              ref="fileInput"
              type="file"
              :accept="imageAccept"
              hidden
              @change="selectImage"
            />
            <button
              type="button"
              class="btn btn-small"
              :disabled="uploading || saving || compressing"
              @click="fileInput?.click()"
            >
              {{ uploading ? '读取中…' : image ? '更换图片' : '选择图片' }}
            </button>
            <span class="image-picker-hint">
              {{ image ? image.file.name : 'JPEG / PNG / WebP / GIF / AVIF' }}
            </span>
          </div>
          <BannerImageInfo v-if="image" :image="image" />
          <small>
            {{
              uploading
                ? '正在读取图片，请稍候…'
                : '支持 JPEG、PNG、WebP、GIF、AVIF；上传时校验最终文件 ≤ 5 MiB，JPEG / PNG 可先压缩为 WebP（需浏览器支持）。'
            }}
          </small>
        </div>
        <WebpCompressionPanel
          v-if="canCompress"
          v-model="webpQuality"
          :original-size="image?.file.size ?? 0"
          :compressed="compressed"
          :disabled="uploading || saving"
          :busy="compressing"
          @compress="compressImage"
          @revert="useOriginal"
        />
        <p v-if="note" class="hint">{{ note }}</p>
        <p v-if="uploaded" class="hint">
          图片已上传，重试仅保存配置，不会重复上传。
        </p>
        <div class="form-grid">
          <label class="field">
            <span>
              跳转地址
              <small>可选</small>
            </span>
            <input
              v-model="link"
              type="text"
              placeholder="/novel 或 https://example.com"
              autocomplete="off"
            />
            <small>留空则不跳转。支持站内路径或完整 HTTP(S) 地址。</small>
          </label>
          <label class="field">
            <span>排序值</span>
            <input
              v-model.number="sortOrder"
              type="number"
              step="1"
              min="-2147483648"
              max="2147483647"
              required
            />
            <small>数值越小越靠前；相同时按 ID 排序。</small>
          </label>
        </div>
      </fieldset>
      <p v-if="error" class="error-notice" role="alert">{{ error }}</p>
      <div class="form-actions">
        <button
          type="button"
          class="btn"
          :disabled="uploading || saving || compressing"
          @click="emit('close')"
        >
          取消
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :disabled="uploading || saving || compressing"
        >
          {{
            uploading
              ? '读取图片中…'
              : saving
                ? '正在提交…'
                : image
                  ? '上传并保存'
                  : '保存 Banner'
          }}
        </button>
      </div>
    </form>
  </XDialog>
</template>

<style scoped src="./banner.shared.css"></style>

<style scoped>
.image-field {
  margin-bottom: 12px;
}
.image-picker {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px dashed var(--color-border);
  border-radius: 8px;
  background: var(--color-embedded);
}
.image-picker-hint {
  min-width: 0;
  color: var(--color-muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}
</style>
