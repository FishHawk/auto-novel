<script setup lang="ts">
import { ref } from 'vue';

defineProps<{ src: string; alt: string }>();

// 尺寸对齐首页 banner 的实际裁剪区域（见 web/src/pages/home/Home.vue）：
// 桌面 1440 视口、侧栏展开 240px，内容区 1200px，高度 42×1.6 + 20 + 40 + 20 + 50 ≈ 197px；
// 移动 390 视口，高度 42×1.6 + 20 + 40 + 10 + 35 ≈ 172px。
const modes = [
  { value: 'original', label: '原图', width: 0, height: 0, books: 0 },
  { value: 'desktop', label: '桌面模拟', width: 1200, height: 197, books: 6 },
  { value: 'mobile', label: '移动模拟', width: 390, height: 172, books: 3 },
] as const;
const mode = ref<(typeof modes)[number]>(modes[0]);
</script>

<template>
  <div class="banner-preview">
    <div class="preview-toolbar">
      <div class="filter-tabs" aria-label="预览方式">
        <button
          v-for="option in modes"
          :key="option.value"
          type="button"
          :class="{ selected: mode.value === option.value }"
          :aria-pressed="mode.value === option.value"
          @click="mode = option"
        >
          {{ option.label }}
        </button>
      </div>
      <span v-if="mode.value !== 'original'" class="preview-caption">
        模拟裁剪 · {{ mode.width }} × {{ mode.height }} px
      </span>
      <div v-if="$slots.actions" class="preview-actions">
        <slot name="actions" />
      </div>
    </div>
    <div
      class="preview-stage"
      :class="{ 'mobile-stage': mode.value === 'mobile' }"
    >
      <img
        v-if="mode.value === 'original'"
        class="original-image"
        :src="src"
        :alt="alt"
      />
      <div
        v-else
        class="device-preview"
        :class="mode.value"
        :style="{
          '--banner-ratio': `${mode.width} / ${mode.height}`,
          '--device-width': `${mode.width}px`,
          '--book-columns': mode.books,
        }"
      >
        <div
          v-if="mode.value === 'desktop'"
          class="browser-chrome"
          aria-hidden="true"
        >
          <div class="window-dots">
            <i v-for="index in 3" :key="index"></i>
          </div>
          <span class="browser-address">novelia · 首页</span>
          <span class="browser-menu">···</span>
        </div>
        <div v-else class="phone-status" aria-hidden="true">
          <span>9:41</span>
          <span class="phone-island"></span>
          <span>▮▮▮ ▰</span>
        </div>
        <div class="mock-page">
          <div class="mock-nav" aria-hidden="true">
            <strong>轻小说机翻机器人</strong>
            <div v-if="mode.value === 'desktop'" class="mock-nav-links">
              <span v-for="label in ['首页', '小说', '论坛']" :key="label">
                {{ label }}
              </span>
            </div>
            <span class="mock-account">
              {{ mode.value === 'desktop' ? '登录 / 注册' : '☰' }}
            </span>
          </div>
          <div class="mock-banner">
            <img :src="src" :alt="alt" />
            <div class="mock-banner-content" aria-hidden="true">
              <strong>轻小说机翻机器人</strong>
              <div class="mock-search">
                <span>搜索小说或输入链接…</span>
                <span>搜索</span>
              </div>
            </div>
          </div>
          <div class="mock-content" aria-hidden="true">
            <div class="mock-content-heading">
              <strong>最近更新</strong>
              <span>查看全部 →</span>
            </div>
            <div class="mock-books">
              <div v-for="index in mode.books" :key="index" class="mock-book">
                <div class="mock-cover"></div>
                <div class="mock-line"></div>
                <div class="mock-line short"></div>
              </div>
            </div>
          </div>
        </div>
        <div
          v-if="mode.value === 'mobile'"
          class="phone-bottom"
          aria-hidden="true"
        >
          <span></span>
        </div>
      </div>
    </div>
    <p v-if="mode.value !== 'original'" class="preview-note">
      设备与页面仅作效果示意，实际裁剪随首页视口宽度变化；上传文件不会被裁剪。
    </p>
  </div>
</template>

<style scoped src="./banner.shared.css"></style>

<style scoped>
.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 20px;
}
.preview-actions {
  margin-left: auto;
}
.preview-toolbar .filter-tabs {
  padding: 3px;
  border-radius: 8px;
  background: var(--color-embedded);
}
.preview-toolbar .filter-tabs button {
  border-radius: 6px;
  transition:
    background 150ms ease,
    color 150ms ease;
}
.preview-toolbar .filter-tabs button.selected {
  background: var(--color-surface);
}
.preview-stage {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: 560px;
  overflow: hidden;
  margin: 0 20px;
  padding: 20px;
  border-radius: 10px;
  background: var(--color-embedded);
}
.mobile-stage {
  height: auto;
  min-height: 560px;
}
.original-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.device-preview {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: 100%;
  box-sizing: border-box;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
  color: var(--color-ink);
  box-shadow: 0 12px 32px rgb(0 0 0 / 8%);
  container-type: inline-size;
}
.mock-page {
  min-height: 0;
  overflow: auto;
}
.browser-chrome,
.phone-status,
.phone-bottom {
  flex-shrink: 0;
}
.browser-chrome {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-divider);
  background: var(--color-hover);
  font-size: 11px;
  color: var(--color-muted);
}
.window-dots {
  display: flex;
  gap: 5px;
}
.window-dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-muted);
  opacity: 0.45;
}
.browser-address {
  flex: 1;
  padding: 4px 12px;
  border-radius: 5px;
  background: var(--color-surface);
  text-align: center;
}
.mock-nav {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 14px 5%;
  font-size: clamp(9px, 1.3cqw, 14px);
}
.mock-nav-links {
  display: flex;
  gap: 20px;
  color: var(--color-muted);
}
.mock-account {
  margin-left: auto;
  color: var(--color-muted);
  white-space: nowrap;
}
.mock-banner {
  position: relative;
  aspect-ratio: var(--banner-ratio);
  overflow: hidden;
}
.mock-banner img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center top;
}
.mock-banner-content {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 16px;
  background: rgb(0 0 0 / 45%);
}
.mock-banner-content > strong {
  color: white;
  font-size: clamp(12px, 2.8cqw, 32px);
  text-shadow: 0 2px 4px rgb(0 0 0 / 40%);
}
.mock-search {
  display: flex;
  justify-content: space-between;
  width: 60%;
  overflow: hidden;
  border-radius: 5px;
  background: var(--color-surface);
  color: var(--color-muted);
  font-size: clamp(8px, 1.2cqw, 13px);
}
.mock-search span {
  padding: 9px 12px;
}
.mock-search span:last-child {
  background: var(--color-primary);
  color: var(--color-on-primary);
  white-space: nowrap;
}
.mock-content {
  padding: 20px 5%;
}
.mock-content-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  font-size: clamp(10px, 1.4cqw, 14px);
}
.mock-content-heading > span {
  font-size: 10px;
  color: var(--color-muted);
}
.mock-books {
  display: grid;
  grid-template-columns: repeat(var(--book-columns), minmax(0, 1fr));
  gap: 14px;
}
.mock-cover {
  height: clamp(40px, 8cqw, 90px);
  border-radius: 5px;
  background: var(--color-embedded);
}
.mock-line {
  height: 5px;
  margin-top: 9px;
  border-radius: 3px;
  background: var(--color-hover);
}
.mock-line.short {
  width: 60%;
}
.mobile {
  max-height: none;
  max-width: var(--device-width);
  border: 5px solid var(--color-ink);
  border-radius: 30px;
}
.phone-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  font-size: 11px;
  font-weight: 600;
}
.phone-island {
  width: 72px;
  height: 18px;
  border-radius: 20px;
  background: var(--color-ink);
}
.mobile .mock-page {
  overflow: visible;
}
.mobile .mock-nav {
  padding: 12px 16px;
  font-size: 12px;
}
.mobile .mock-banner-content > strong {
  font-size: clamp(16px, 6cqw, 24px);
}
.mobile .mock-search {
  width: 100%;
  font-size: 11px;
}
.mobile .mock-books {
  gap: 10px;
}
.mobile .mock-cover {
  height: 90px;
}
.phone-bottom {
  display: flex;
  justify-content: center;
  padding: 12px;
}
.phone-bottom span {
  width: 100px;
  height: 4px;
  border-radius: 4px;
  background: var(--color-ink);
}
.preview-caption {
  white-space: nowrap;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.7;
}
.preview-note {
  margin: 12px 20px 0;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.7;
}
@media (max-width: 767px) {
  .preview-toolbar {
    padding: 12px;
  }
  .preview-stage {
    margin: 0 12px;
    padding: 12px;
  }
  .preview-note {
    margin-inline: 12px;
  }
}
</style>
