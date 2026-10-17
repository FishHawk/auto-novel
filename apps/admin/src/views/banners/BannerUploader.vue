<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { XDropZone, getApiErrorMessage } from '@novelia/web-kit';
import { imageAccept, useBannerApi, validLink } from '../../api';
import {
  loadBannerImage,
  prepareBannerImage,
  validateDimensions,
  type BannerImage,
} from './bannerImage';
import BannerImageFields from './BannerImageFields.vue';

const props = defineProps<{ disabled?: boolean }>();
const emit = defineEmits<{ saved: []; busy: [value: boolean] }>();
const api = useBannerApi();
interface PendingBanner {
  id: number;
  image: BannerImage;
  link: string;
  sortOrder: number;
  uploaded?: { key: string; imageUrl: string };
  error: string;
  status: string;
}
const files = ref<PendingBanner[]>([]);
const input = ref<HTMLInputElement>();
const loading = ref(false);
const uploading = ref(false);
const error = ref('');
const notice = ref('');
defineExpose({
  open: () => {
    if (!props.disabled && !loading.value && !uploading.value)
      input.value?.click();
  },
});
let nextId = 0;
let disposed = false;
const controller = new AbortController();

async function add(selected: File[]) {
  if (props.disabled || disposed || uploading.value || loading.value) return;
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
  files.value = files.value.filter((value) => value.id !== item.id);
}
async function upload() {
  if (
    props.disabled ||
    disposed ||
    uploading.value ||
    loading.value ||
    !files.value.length
  )
    return;
  error.value = '';
  notice.value = '';
  // 先检查整批配置，避免输入错误导致部分上传。
  try {
    for (const item of files.value) {
      validateDimensions(item.image);
      if (!validLink(item.link))
        throw new Error(
          `${item.image.file.name}：跳转地址必须为站内路径或 HTTP(S) 地址`,
        );
      if (
        !Number.isInteger(item.sortOrder) ||
        item.sortOrder < -2147483648 ||
        item.sortOrder > 2147483647
      )
        throw new Error(
          `${item.image.file.name}：排序值必须为有效的 32 位整数`,
        );
    }
  } catch (cause) {
    error.value = getApiErrorMessage(cause, '配置无效');
    return;
  }
  uploading.value = true;
  emit('busy', true);
  let saved = 0;
  try {
    for (const item of [...files.value]) {
      if (disposed) break;
      item.error = '';
      try {
        if (!item.uploaded) {
          item.status = '处理图片…';
          const file = await prepareBannerImage(item.image);
          item.status = '正在上传…';
          item.uploaded = await api.upload(file, {
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
  for (const item of files.value) URL.revokeObjectURL(item.image.url);
});
</script>

<template>
  <XDropZone :disabled="disabled || loading || uploading" @drop="add">
    松开以加载 Banner 图片（不会立即上传）
  </XDropZone>
  <section
    class="banner-panel upload-panel"
    aria-label="加载和上传 Banner"
    :aria-busy="uploading"
  >
    <div class="panel-toolbar">
      <div>
        <h2>加载 Banner 图片</h2>
        <p class="hint">拖动图片到页面，或点击加载。确认尺寸和配置后再上传。</p>
      </div>
      <button
        type="button"
        class="btn"
        :disabled="disabled || loading || uploading"
        @click="input?.click()"
      >
        {{ loading ? '读取中…' : '加载图片' }}
      </button>
      <input
        ref="input"
        type="file"
        :accept="imageAccept"
        multiple
        hidden
        @change="select"
      />
    </div>
    <p v-if="!files.length" class="upload-placeholder">
      支持 JPEG、PNG、WebP、GIF、AVIF，每张最大 5 MiB。加载仅在本地进行。
    </p>
    <form class="banner-form upload-form" @submit.prevent="upload">
      <fieldset :disabled="disabled || uploading">
        <article v-for="item in files" :key="item.id" class="pending-banner">
          <div class="pending-heading">
            <strong>{{ item.image.file.name }}</strong>
            <button
              type="button"
              class="text-button danger"
              @click="remove(item)"
            >
              移除
            </button>
          </div>
          <BannerImageFields :image="item.image" v-if="!item.uploaded" />
          <img
            v-else
            :src="item.image.url"
            :alt="item.image.file.name"
            class="form-preview"
          />
          <p v-if="item.uploaded" class="hint">
            图片已上传，重试仅保存配置，不会重复上传。需要修改尺寸请移除并重新加载。
          </p>
          <label class="field">
            <span>跳转地址（可选）</span>
            <input
              v-model="item.link"
              placeholder="/novel 或 https://example.com"
            />
          </label>
          <label class="field">
            <span>排序值</span>
            <input
              v-model.number="item.sortOrder"
              type="number"
              min="-2147483648"
              max="2147483647"
              step="1"
              required
            />
          </label>
          <p role="status" class="hint">{{ item.status }}</p>
          <p v-if="item.error" class="error-notice" role="alert">
            {{ item.error }}
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
          :disabled="disabled || loading || uploading"
        >
          {{ uploading ? '正在上传…' : '上传' }}
        </button>
      </div>
    </form>
  </section>
</template>
