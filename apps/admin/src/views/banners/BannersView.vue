<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { XDialog, getApiErrorMessage } from '@novelia/web-kit';
import { useBannerApi, type Banner } from '../../api';
import BannerForm from './BannerForm.vue';
import BannerUploader from './BannerUploader.vue';
import BannerPreview from './BannerPreview.vue';

const activeTab = ref('manage');
const tabs = [
  { value: 'manage', label: '管理' },
  { value: 'upload', label: '上传' },
];
function navigateTabs(event: KeyboardEvent) {
  const direction =
    event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
  if (!direction && event.key !== 'Home' && event.key !== 'End') return;
  event.preventDefault();
  const index = tabs.findIndex((tab) => tab.value === activeTab.value);
  const next =
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? tabs.length - 1
        : (index + direction + tabs.length) % tabs.length;
  activeTab.value = tabs[next]!.value;
  const tablist = event.currentTarget as HTMLElement;
  tablist.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus();
}
const uploading = ref(false);

const api = useBannerApi();
const items = ref<Banner[]>([]);
const loading = ref(false);
const loaded = ref(false);
const error = ref('');
const notice = ref('');
const busy = ref<number | null>(null);
const editor = ref<Banner | null>(null);
const deleting = ref<Banner | null>(null);
const deleteError = ref('');
const selectedId = ref<number>();
const selected = computed(
  () =>
    items.value.find((item) => item.id === selectedId.value) ?? items.value[0],
);

let reloadRequested = false;
async function load() {
  if (loading.value) {
    reloadRequested = true;
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    items.value = await api.list();
    loaded.value = true;
  } catch (cause) {
    error.value = getApiErrorMessage(cause, '操作失败');
  } finally {
    loading.value = false;
    if (reloadRequested) {
      reloadRequested = false;
      void load();
    }
  }
}

async function toggle(banner: Banner) {
  if (busy.value !== null || loading.value) return;
  busy.value = banner.id;
  error.value = '';
  notice.value = '';
  try {
    await api.setActive(banner.id, !banner.isActive);
    banner.isActive = !banner.isActive;
    notice.value = `Banner #${banner.id} 已${banner.isActive ? '启用' : '停用'}`;
  } catch (cause) {
    error.value = getApiErrorMessage(cause, '操作失败');
  } finally {
    busy.value = null;
  }
}

async function remove() {
  const banner = deleting.value;
  if (!banner || busy.value !== null) return;
  busy.value = banner.id;
  deleteError.value = '';
  try {
    await api.remove(banner.id);
    items.value = items.value.filter((item) => item.id !== banner.id);
    deleting.value = null;
    notice.value = `Banner #${banner.id} 已删除`;
  } catch (cause) {
    deleteError.value = getApiErrorMessage(cause, '操作失败');
  } finally {
    busy.value = null;
  }
}

async function saved() {
  editor.value = null;
  notice.value = 'Banner 已保存';
  await load();
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}
onMounted(load);
</script>

<template>
  <header class="page-heading">
    <div>
      <h1>Banner 管理</h1>
      <p class="subtitle">管理首页轮播图片，让值得关注的内容被看见。</p>
    </div>
  </header>

  <div
    class="banner-tabs"
    role="tablist"
    aria-label="Banner 操作"
    @keydown="navigateTabs"
  >
    <button
      v-for="tab in tabs"
      :id="`banner-tab-${tab.value}`"
      :key="tab.value"
      type="button"
      role="tab"
      :aria-selected="activeTab === tab.value"
      :aria-controls="`banner-panel-${tab.value}`"
      :tabindex="activeTab === tab.value ? 0 : -1"
      @click="activeTab = tab.value"
    >
      {{ tab.label }}
    </button>
  </div>

  <section
    id="banner-panel-upload"
    v-show="activeTab === 'upload'"
    role="tabpanel"
    aria-labelledby="banner-tab-upload"
    tabindex="0"
  >
    <BannerUploader
      :disabled="
        activeTab !== 'upload' || !!editor || !!deleting || busy !== null
      "
      @busy="uploading = $event"
      @saved="load"
    />
  </section>

  <section
    id="banner-panel-manage"
    v-show="activeTab === 'manage'"
    role="tabpanel"
    aria-labelledby="banner-tab-manage"
    tabindex="0"
  >
    <p v-if="notice" class="success-notice" role="status">{{ notice }}</p>
    <p v-if="error" class="error-notice" role="alert">{{ error }}</p>

    <section class="banner-panel" :aria-busy="loading">
      <div v-if="loading || !loaded || !selected" class="panel-toolbar">
        <button
          class="btn btn-small"
          :disabled="loading || busy !== null || uploading"
          @click="load"
        >
          刷新
        </button>
      </div>
      <div v-if="loading" class="empty-state" role="status">
        正在加载 Banner…
      </div>
      <div v-else-if="loaded && !items.length" class="empty-state">
        <h2>还没有 Banner</h2>
        <p>切换到上传页，添加第一张首页轮播图片。</p>
      </div>
      <template v-else-if="loaded && selected">
        <BannerPreview
          :src="selected.imageUrl"
          :alt="`Banner #${selected.id}`"
          :focal-x="selected.focalX"
          :focal-y="selected.focalY"
        >
          <template #actions>
            <button
              type="button"
              class="btn"
              :disabled="loading || busy !== null || uploading"
              @click="load"
            >
              刷新
            </button>
          </template>
        </BannerPreview>
        <div class="banner-thumbnails" aria-label="Banner 原图列表">
          <button
            v-for="banner in items"
            :key="banner.id"
            type="button"
            class="banner-thumbnail"
            :class="{ selected: selected.id === banner.id }"
            :aria-pressed="selected.id === banner.id"
            :aria-label="`选择 Banner #${banner.id}`"
            :title="`Banner #${banner.id} · ${banner.isActive ? '展示中' : '已停用'}`"
            @click="selectedId = banner.id"
          >
            <img
              :src="banner.imageUrl"
              :alt="`Banner #${banner.id}`"
              loading="lazy"
            />
          </button>
        </div>
        <div class="selected-banner-details">
          <div class="selected-banner-heading">
            <strong>Banner #{{ selected.id }}</strong>
            <span class="status-badge" :class="{ active: selected.isActive }">
              {{ selected.isActive ? '展示中' : '已停用' }}
            </span>
          </div>
          <p class="hint">跳转地址：{{ selected.linkUrl || '不跳转' }}</p>
          <p class="hint">
            排序：{{ selected.sortOrder }} · 更新于
            {{ formatDate(selected.updatedAt) }}
          </p>
          <div class="row-actions">
            <button
              class="btn btn-small"
              :disabled="busy !== null || uploading"
              @click="editor = selected"
            >
              编辑
            </button>
            <button
              class="btn btn-small"
              :disabled="busy !== null || uploading"
              @click="toggle(selected)"
            >
              {{
                busy === selected.id
                  ? '处理中…'
                  : selected.isActive
                    ? '停用'
                    : '启用'
              }}
            </button>
            <button
              class="btn btn-small danger"
              :disabled="busy !== null || uploading"
              @click="
                deleting = selected;
                deleteError = '';
              "
            >
              删除
            </button>
          </div>
        </div>
      </template>
      <footer class="panel-footer">
        首页轮播 · 按排序值从小到大展示，排序相同时按 ID 排序。
      </footer>
    </section>
  </section>

  <BannerForm
    v-if="editor"
    :banner="editor"
    @close="editor = null"
    @saved="saved"
  />
  <XDialog
    :open="!!deleting"
    title="删除 Banner"
    description="删除后无法恢复，关联图片也会被清理。"
    :closable="busy === null"
    @update:open="!$event && (deleting = null)"
  >
    <p class="delete-description">
      确定删除 Banner #{{ deleting?.id }} 吗？{{
        deleting?.isActive ? '此图片目前正在首页展示。' : ''
      }}
    </p>
    <p v-if="deleteError" class="error-notice" role="alert">
      {{ deleteError }}
    </p>
    <div class="form-actions">
      <button
        class="btn"
        :disabled="busy !== null || uploading"
        @click="deleting = null"
      >
        取消
      </button>
      <button
        class="btn btn-danger"
        :disabled="busy !== null || uploading"
        @click="remove"
      >
        {{ busy !== null ? '正在删除…' : '确认删除' }}
      </button>
    </div>
  </XDialog>
</template>

<style scoped src="./banner.shared.css"></style>

<style scoped>
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 28px;
}
.subtitle {
  margin: 8px 0 0;
  color: var(--color-muted);
  font-size: 14px;
}
.banner-tabs {
  display: inline-flex;
  gap: 4px;
  margin-bottom: 24px;
  padding: 4px;
  border-radius: 10px;
  background: var(--color-embedded);
}
.banner-tabs button {
  min-width: 88px;
  padding: 8px 20px;
  border: none;
  border-radius: 7px;
  color: var(--color-muted);
  font-size: 14px;
}
.banner-tabs button[aria-selected='true'] {
  background: var(--color-surface);
  color: var(--color-primary);
  font-weight: 600;
}
.selected-banner-details {
  padding: 20px;
  overflow-wrap: anywhere;
}
.selected-banner-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.selected-banner-details .hint {
  margin: 12px 0;
}
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  border-radius: 20px;
  background: var(--color-hover);
  color: var(--color-muted);
  padding: 4px 9px;
}
.status-badge::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.status-badge.active {
  background: var(--color-success-soft);
  color: var(--color-success);
}
.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 14px;
}
.panel-footer {
  padding: 14px 20px;
  color: var(--color-muted);
  font-size: 12px;
}
.delete-description {
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.7;
  margin: 12px 0 20px;
}
@media (max-width: 767px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
