<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { XDialog, XDropZone, getApiErrorMessage } from '@novelia/web-kit';
import { loadBannerImage, type BannerImage } from './bannerImage';
import { validateBannerConfig } from './bannerConfig';
import BannerImageInfo from './BannerImageInfo.vue';
import BannerPreview from './BannerPreview.vue';
import { imageAccept, useBannerApi, type Banner } from '../../api';

const props = defineProps<{ banner: Banner }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const api = useBannerApi();
const link = ref(props.banner.linkUrl ?? '');
const sortOrder = ref(props.banner.sortOrder);
const uploading = ref(false);
const saving = ref(false);
const error = ref('');
const image = ref<BannerImage>();
const uploaded = ref<{ key: string; imageUrl: string }>();
const controller = new AbortController();
let disposed = false;

async function selectFiles(files: File[]) {
  if (saving.value || uploading.value || !files.length) return;
  if (files.length !== 1) {
    error.value = '编辑时请只加载一张替换图片';
    return;
  }
  error.value = '';
  uploading.value = true;
  try {
    const next = await loadBannerImage(files[0]!);
    if (disposed) {
      URL.revokeObjectURL(next.url);
      return;
    }
    if (image.value) URL.revokeObjectURL(image.value.url);
    image.value = next;
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
});

async function save() {
  if (saving.value || uploading.value) return;
  error.value = validateBannerConfig(link.value, sortOrder.value);
  if (error.value) return;
  saving.value = true;
  try {
    if (image.value && !uploaded.value) {
      uploaded.value = await api.upload(image.value.file, {
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
  <XDropZone :disabled="uploading || saving" @drop="selectFiles">
    松开以加载替换图片（不会立即上传）
  </XDropZone>
  <XDialog
    :open="true"
    :title="`编辑 Banner #${banner.id}`"
    description="管理首页轮播图片、跳转地址和展示顺序。"
    :closable="!uploading && !saving"
    @update:open="!$event && emit('close')"
  >
    <form class="banner-form" @submit.prevent="save">
      <fieldset :disabled="uploading || saving">
        <BannerPreview
          :src="image?.url || banner.imageUrl"
          :alt="image?.file.name || 'Banner 图片预览'"
        />
        <label class="field">
          <span>
            图片
            <span class="required">*</span>
          </span>
          <span class="upload-area">
            <span class="upload-placeholder">
              加载一张首页轮播图片，确认后再上传
            </span>
            <input
              type="file"
              :accept="imageAccept"
              aria-label="上传 Banner 图片"
              @change="selectImage"
            />
          </span>
          <small>
            {{
              uploading
                ? '正在读取图片，请稍候…'
                : '支持 JPEG、PNG、WebP、GIF、AVIF，最大 5 MiB。'
            }}
          </small>
        </label>
        <BannerImageInfo v-if="image" :image="image" />
        <p v-if="uploaded" class="hint">
          图片已上传，重试仅保存配置，不会重复上传。
        </p>
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
      </fieldset>
      <p v-if="error" class="error-notice" role="alert">{{ error }}</p>
      <div class="form-actions">
        <button
          type="button"
          class="btn"
          :disabled="uploading || saving"
          @click="emit('close')"
        >
          取消
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :disabled="uploading || saving"
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
.upload-area {
  border: 1px dashed var(--color-border);
  border-radius: 6px;
  overflow: hidden;
  background: var(--color-embedded);
}
.upload-area input {
  display: block;
  width: 100%;
  padding: 12px;
  font-size: 12px;
}
.upload-area input::file-selector-button {
  padding: 5px 10px;
  background: var(--color-surface);
  color: var(--color-ink);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  margin-right: 10px;
  cursor: pointer;
}
</style>
