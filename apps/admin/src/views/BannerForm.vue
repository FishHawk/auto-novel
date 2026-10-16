<script setup lang="ts">
import { ref } from 'vue';
import { XDialog, getApiErrorMessage } from '@novelia/web-kit';
import {
  imageAccept,
  useBannerApi,
  validLink,
  validateImage,
  type Banner,
} from '../api';

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
const filename = ref('');

async function selectImage(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  error.value = '';
  try {
    validateImage(file);
    uploading.value = true;
    const uploaded = await api.upload(file);
    key.value = uploaded.key;
    preview.value = uploaded.imageUrl;
    filename.value = file.name;
  } catch (cause) {
    error.value = getApiErrorMessage(cause, '操作失败');
  } finally {
    uploading.value = false;
    input.value = '';
  }
}

async function save() {
  if (saving.value || uploading.value) return;
  error.value = '';
  if (!key.value) {
    error.value = '请先上传一张 Banner 图片';
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
              v-if="preview"
              :src="preview"
              alt="Banner 图片预览"
              class="form-preview"
            />
            <span v-else class="upload-placeholder">选择一张首页轮播图片</span>
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
                ? '正在上传图片，请稍候…'
                : filename || '支持 JPEG、PNG、WebP、GIF、AVIF，最大 5 MiB。'
            }}
          </small>
        </label>
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
          {{ uploading ? '图片上传中…' : saving ? '正在保存…' : '保存 Banner' }}
        </button>
      </div>
    </form>
  </XDialog>
</template>
