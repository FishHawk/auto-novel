<script lang="ts" setup>
import { useWindowSize } from '@vueuse/core';

import type { ImageRenderToolbarProps } from 'naive-ui';

const props = defineProps<{
  images: {
    path: string;
    url: string;
  }[];
}>();
const selectedImgs = defineModel<string[]>('value', { required: true });

const height = useWindowSize().height;

const showOnlySelectedImgs = ref(false);
const shownImages = computed(() => {
  if (showOnlySelectedImgs.value) {
    return [...props.images].filter((e) => selectedImgs.value.includes(e.path));
  }
  return props.images;
});

/**
 * 创建显示图片的工具栏
 * 在工具栏中显示图片的完整路径
 */
const createRenderToolbar = (imgPath: string) => {
  const renderToolbar = ({ nodes }: ImageRenderToolbarProps) => {
    return [h('span', {}, imgPath), nodes.zoomOut, nodes.zoomIn, nodes.close];
  };
  return renderToolbar;
};
</script>

<template>
  <c-modal title="选择图片" content-style="padding: 0;">
    <n-flex vertical size="large" style="width: 100%; padding: 20px">
      <n-checkbox-group v-model:value="selectedImgs">
        <n-grid cols="4">
          <n-gi v-for="image in shownImages" :key="image.path">
            <n-card size="small" hoverable>
              <template #footer>
                <n-checkbox :value="image.path">
                  {{ image.path.split('/').pop() }}
                </n-checkbox>
              </template>
              <n-image
                width="100px"
                :src="image.url"
                :render-toolbar="createRenderToolbar(image.path)"
              />
            </n-card>
          </n-gi>
          <!-- 占位符, 防止图片少时窗口大小变化 -->
          <n-gi
            v-for="i in Math.max(0, 12 - shownImages.length)"
            :key="'placeholder-' + i"
          >
            <n-p
              :style="`width: 100px; height: ${height * 0.2}px; visibility: hidden;`"
            ></n-p>
          </n-gi>
        </n-grid>
      </n-checkbox-group>
    </n-flex>
    <template #action>
      <n-flex align="center">
        仅显示选中
        <n-switch size="small" v-model:value="showOnlySelectedImgs" />
        <c-button
          label="清空"
          size="tiny"
          @action="selectedImgs = []"
          type="error"
        />
      </n-flex>
    </template>
  </c-modal>
</template>
