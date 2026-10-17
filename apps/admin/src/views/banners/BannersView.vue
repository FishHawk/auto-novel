<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { XDialog, getApiErrorMessage } from '@novelia/web-kit';
import { useBannerApi, type Banner } from '../../api';
import BannerForm from './BannerForm.vue';
import BannerUploader from './BannerUploader.vue';

const uploader = ref<InstanceType<typeof BannerUploader>>();
const uploading = ref(false);

const api = useBannerApi();
const items = ref<Banner[]>([]);
const loading = ref(false);
const loaded = ref(false);
const error = ref('');
const notice = ref('');
const filter = ref('all');
const busy = ref<number | null>(null);
const editor = ref<{ banner?: Banner } | null>(null);
const deleting = ref<Banner | null>(null);
const deleteError = ref('');
const activeCount = computed(
  () => items.value.filter((item) => item.isActive).length,
);
const visible = computed(() =>
  items.value.filter(
    (item) =>
      filter.value === 'all' || item.isActive === (filter.value === 'active'),
  ),
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
      <p class="eyebrow">CONTENT / HOME</p>
      <h1>Banner 管理</h1>
      <p class="subtitle">管理首页轮播图片，让值得关注的内容被看见。</p>
    </div>
    <button
      class="btn btn-primary"
      :disabled="loading || busy !== null || uploading"
      @click="uploader?.open()"
    >
      加载图片
    </button>
  </header>

  <BannerUploader
    ref="uploader"
    :disabled="!!editor || !!deleting || busy !== null"
    @busy="uploading = $event"
    @saved="load"
  />

  <section class="metrics" aria-label="Banner 统计">
    <div>
      <span>全部 Banner</span>
      <strong>{{ loaded ? items.length : '—' }}</strong>
    </div>
    <div>
      <span>展示中</span>
      <strong class="text-success">{{ loaded ? activeCount : '—' }}</strong>
    </div>
    <div>
      <span>已停用</span>
      <strong>{{ loaded ? items.length - activeCount : '—' }}</strong>
    </div>
  </section>

  <p v-if="notice" class="success-notice" role="status">{{ notice }}</p>
  <div v-if="error" class="error-notice" role="alert">
    {{ error }}
    <button class="text-button" :disabled="loading" @click="load">
      重新加载
    </button>
  </div>

  <section class="banner-panel" :aria-busy="loading">
    <div class="panel-toolbar">
      <div class="filter-tabs" aria-label="按状态筛选">
        <button
          v-for="option in [
            { value: 'all', label: '全部' },
            { value: 'active', label: '展示中' },
            { value: 'inactive', label: '已停用' },
          ]"
          :key="option.value"
          :class="{ selected: filter === option.value }"
          :aria-pressed="filter === option.value"
          @click="filter = option.value"
        >
          {{ option.label }}
        </button>
      </div>
      <button
        class="btn btn-small"
        :disabled="loading || busy !== null || uploading"
        @click="load"
      >
        {{ loading ? '加载中…' : '刷新' }}
      </button>
    </div>
    <div v-if="loading" class="empty-state" role="status">正在加载 Banner…</div>
    <div v-else-if="loaded && !visible.length" class="empty-state">
      <h2>{{ items.length ? '此状态下暂无 Banner' : '还没有 Banner' }}</h2>
      <p>
        {{
          items.length
            ? '可以切换筛选查看其他内容。'
            : '上传第一张图片，开始配置首页轮播。'
        }}
      </p>
      <button v-if="!items.length" class="btn" @click="uploader?.open()">
        新增 Banner
      </button>
    </div>
    <div v-else-if="loaded" class="table-scroll">
      <table>
        <thead>
          <tr>
            <th scope="col">图片 / 编号</th>
            <th scope="col">跳转地址</th>
            <th scope="col">排序</th>
            <th scope="col">状态</th>
            <th scope="col">更新时间</th>
            <th scope="col" class="actions-heading">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="banner in visible" :key="banner.id">
            <td>
              <div class="banner-identity">
                <img
                  :src="banner.imageUrl"
                  :alt="`Banner #${banner.id}`"
                  loading="lazy"
                />
                <span>#{{ banner.id }}</span>
              </div>
            </td>
            <td>
              <span class="link-cell" :title="banner.linkUrl ?? ''">
                {{ banner.linkUrl || '不跳转' }}
              </span>
            </td>
            <td class="numeric">{{ banner.sortOrder }}</td>
            <td>
              <span class="status-badge" :class="{ active: banner.isActive }">
                {{ banner.isActive ? '展示中' : '已停用' }}
              </span>
            </td>
            <td class="date-cell">{{ formatDate(banner.updatedAt) }}</td>
            <td>
              <div class="row-actions">
                <button
                  class="text-button"
                  :disabled="busy !== null || uploading"
                  @click="editor = { banner }"
                >
                  编辑
                </button>
                <button
                  class="text-button"
                  :disabled="busy !== null || uploading"
                  @click="toggle(banner)"
                >
                  {{
                    busy === banner.id
                      ? '处理中…'
                      : banner.isActive
                        ? '停用'
                        : '启用'
                  }}
                </button>
                <button
                  class="text-button danger"
                  :disabled="busy !== null || uploading"
                  @click="
                    deleting = banner;
                    deleteError = '';
                  "
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer class="panel-footer">
      首页轮播 · 按排序值从小到大展示，排序相同时按 ID 排序。
    </footer>
  </section>

  <BannerForm
    v-if="editor"
    :banner="editor.banner"
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
