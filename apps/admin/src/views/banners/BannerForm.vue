<script setup lang="ts">
import { ref } from 'vue';
import { Toast, XDialog } from '@novelia/web-kit';
import { validateBannerFocal } from './bannerFocal';
import { validateBannerConfig } from './bannerConfig';
import BannerPreview from './BannerPreview.vue';
import { useBannerApi, type Banner } from '../../api';

const props = defineProps<{ banner: Banner }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const api = useBannerApi();
const link = ref(props.banner.linkUrl ?? '');
const sortOrder = ref(props.banner.sortOrder);
const focalX = ref(props.banner.focalX);
const focalY = ref(props.banner.focalY);
const saving = ref(false);
const error = ref('');

async function save() {
  if (saving.value) return;
  error.value =
    validateBannerConfig(link.value, sortOrder.value) ||
    validateBannerFocal(focalX.value, focalY.value);
  if (error.value) return;
  saving.value = true;
  await Toast.promise(
    api
      .update(props.banner.id, {
        linkUrl: link.value.trim() || null,
        sortOrder: sortOrder.value,
        focalX: focalX.value,
        focalY: focalY.value,
      })
      .then(() => emit('saved')),
    {
      loading: { message: '正在保存 Banner…', duration: 0 },
      success: 'Banner 已保存',
      error: '保存失败',
    },
  );
  saving.value = false;
}
</script>

<template>
  <XDialog
    :open="true"
    :title="`编辑 Banner #${banner.id}`"
    description="编辑跳转地址、展示顺序和图片焦点；更换图片请删除后重新上传。"
    :closable="!saving"
    @update:open="!$event && emit('close')"
  >
    <form class="banner-form" @submit.prevent="save">
      <fieldset :disabled="saving">
        <BannerPreview
          :src="banner.imageUrl"
          v-model:focal-x="focalX"
          v-model:focal-y="focalY"
          editable
          :disabled="saving"
          alt="Banner 图片预览"
        />
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
          :disabled="saving"
          @click="emit('close')"
        >
          取消
        </button>
        <button type="submit" class="btn btn-primary" :disabled="saving">
          {{ saving ? '正在提交…' : '保存 Banner' }}
        </button>
      </div>
    </form>
  </XDialog>
</template>

<style scoped src="./banner.shared.css"></style>
