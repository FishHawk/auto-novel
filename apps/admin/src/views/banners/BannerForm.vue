<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { XDialog, XDropZone, getApiErrorMessage } from '@novelia/web-kit';
import {
  loadBannerImage,
  prepareBannerImage,
  validateDimensions,
  type BannerImage,
} from './bannerImage';
import BannerImageFields from './BannerImageFields.vue';
import { imageAccept, useBannerApi, validLink, type Banner } from '../../api';

const props = defineProps<{ banner?: Banner }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const api = useBannerApi();
const key = ref(props.banner?.key ?? '');
const preview = ref(props.banner?.imageUrl ?? '');
const link = ref(props.banner?.linkUrl ?? '');
const sortOrder = ref(props.banner?.sortOrder ?? 0);
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
  error.value = '';
  if (!key.value && !image.value) {
    error.value = '请先加载一张 Banner 图片';
    return;
  }
  if (!validLink(link.value)) {
    error.value = '跳转地址必须为站内路径或 HTTP(S) 地址';
    return;
  }
  if (
    !Number.isInteger(sortOrder.value) ||
    sortOrder.value < -2147483648 ||
    sortOrder.value > 2147483647
  ) {
    error.value = '排序值必须为有效的 32 位整数';
    return;
  }
  saving.value = true;
  try {
    if (image.value) {
      validateDimensions(image.value);
      if (!uploaded.value) {
        const file = await prepareBannerImage(image.value);
        uploaded.value = await api.upload(file, { signal: controller.signal });
      }
      key.value = uploaded.value.key;
    }
    if (disposed) return;
    await api.save(props.banner?.id, {
      type: 0,
      key: key.value,
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
    :title="banner ? `编辑 Banner #${banner.id}` : '新增 Banner'"
    description="管理首页轮播图片、跳转地址和展示顺序。"
    :closable="!uploading && !saving"
    @update:open="!$event && emit('close')"
  >
    <form class="banner-form" @submit.prevent="save">
      <fieldset :disabled="uploading || saving">
        <label class="field">
          <span>
            图片
            <span class="required">*</span>
          </span>
          <span class="upload-area">
            <img
              v-if="!image && preview"
              :src="preview"
              alt="Banner 图片预览"
              class="form-preview"
            />
            <span v-else class="upload-placeholder">
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
        <fieldset v-if="image" :disabled="!!uploaded">
          <BannerImageFields :image="image" />
        </fieldset>
        <p v-if="uploaded" class="hint">
          图片已上传，重试仅保存配置。如需修改尺寸，请重新加载图片。
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
      <p v-if="!banner" class="hint">
        新建 Banner 默认启用，保存后将在首页展示。
      </p>
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
