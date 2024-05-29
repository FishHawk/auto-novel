<script lang="ts" setup>
import { useKeyModifier } from '@vueuse/core';

import { Locator } from '@/data';
import { Setting } from '@/model/Setting';

import { useIsWideScreen } from '@/pages/util';
import { useBookshelfStore } from '../BookshelfStore';

const props = defineProps<{
  selectedIds: string[];
}>();
defineEmits<{
  selectAll: [];
  invertSelection: [];
}>();

const message = useMessage();
const isWideScreen = useIsWideScreen(600);

const { setting } = Locator.settingRepository();

const store = useBookshelfStore();

const showDownloadModal = ref(false);

const downloadSelected = async () => {
  const ids = props.selectedIds;
  if (ids.length === 0) {
    message.info('没有选中小说');
    return;
  }
  const { success, failed } = await store.downloadVolumes(ids);
  message.info(`${success}本小说被打包，${failed}本失败`);
};

const translateLevel = ref<'expire' | 'all'>('expire');
const reverseOrder = ref(false);
const shouldTopJob = useKeyModifier('Control');

const queueJobs = (type: 'gpt' | 'sakura') => {
  const ids = props.selectedIds;
  if (ids.length === 0) {
    message.info('没有选中小说');
    return;
  }
  const { success, failed } = store.queueJobsToWorkspace(ids, {
    level: translateLevel.value,
    type,
    shouldTop: shouldTopJob.value ?? false,
  });
  message.info(`${success}本小说已排队，${failed}本失败`);
};
</script>

<template>
  <n-list bordered>
    <n-list-item>
      <n-flex vertical>
        <c-action-wrapper title="选择">
          <n-flex align="baseline">
            <n-button-group size="small">
              <c-button
                label="全选"
                :round="false"
                @action="$emit('selectAll')"
              />
              <c-button
                label="反选"
                :round="false"
                @action="$emit('invertSelection')"
              />
            </n-button-group>
            <n-text depth="3"> 已选择{{ selectedIds.length }}本小说 </n-text>
          </n-flex>
        </c-action-wrapper>

        <c-action-wrapper title="操作">
          <n-button-group size="small">
            <c-button label="下载" :round="false" @action="downloadSelected" />
            <c-button
              label="下载设置"
              :round="false"
              @action="showDownloadModal = true"
            />
            <bookshelf-delete-button
              :selected-ids="selectedIds"
              :round="false"
            />
          </n-button-group>
        </c-action-wrapper>
      </n-flex>
    </n-list-item>

    <n-list-item
      v-if="
        setting.enabledTranslator.includes('gpt') ||
        setting.enabledTranslator.includes('sakura')
      "
    >
      <n-flex vertical>
        <b>批量生成翻译任务</b>

        <c-action-wrapper title="选项">
          <n-flex size="small">
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-flex :size="0" :wrap="false">
                  <tag-button
                    label="过期"
                    :checked="translateLevel === 'expire'"
                    @update:checked="translateLevel = 'expire'"
                  />
                  <tag-button
                    label="重翻"
                    type="warning"
                    :checked="translateLevel === 'all'"
                    @update:checked="translateLevel = 'all'"
                  />
                </n-flex>
              </template>
              过期：翻译术语表过期的章节<br />
              重翻：重翻全部章节<br />
            </n-tooltip>

            <tag-button label="倒序添加" v-model:checked="reverseOrder" />
          </n-flex>
        </c-action-wrapper>

        <c-action-wrapper title="操作">
          <n-button-group size="small">
            <c-button
              v-if="setting.enabledTranslator.includes('gpt')"
              label="排队GPT"
              :round="false"
              @action="queueJobs('gpt')"
            />
            <c-button
              v-if="setting.enabledTranslator.includes('sakura')"
              label="排队Sakura"
              :round="false"
              @action="queueJobs('sakura')"
            />
          </n-button-group>
        </c-action-wrapper>
      </n-flex>
    </n-list-item>
  </n-list>

  <c-modal title="下载设置" v-model:show="showDownloadModal">
    <n-flex vertical size="large">
      <c-action-wrapper title="语言">
        <c-radio-group
          v-model:value="setting.downloadFormat.mode"
          :options="Setting.downloadModeOptions"
        />
      </c-action-wrapper>

      <c-action-wrapper title="翻译">
        <n-flex>
          <c-radio-group
            v-model:value="setting.downloadFormat.translationsMode"
            :options="Setting.downloadTranslationModeOptions"
          />
          <translator-check
            v-model:value="setting.downloadFormat.translations"
            show-order
            :two-line="!isWideScreen"
          />
        </n-flex>
      </c-action-wrapper>

      <n-text depth="3" style="font-size: 12px">
        # 某些EPUB阅读器无法正确显示日文段落的浅色字体
      </n-text>
    </n-flex>
  </c-modal>
</template>
