<script lang="ts" setup>
import { ref, watch } from 'vue';

import {
  downloadModeOptions,
  downloadTranslationModeOptions,
  downloadTypeOptions,
  useSettingStore,
} from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';
import { useIsWideScreen } from '@/data/util';

defineProps<{
  type: 'web' | 'wenku';
  glossary: { [key: string]: string };
  submit: () => Promise<void>;
}>();

const userData = useUserDataStore();
const setting = useSettingStore();
const isWideScreen = useIsWideScreen(600);

// 翻译设置
const showGlossaryModal = ref(false);
const translateExpireChapter = ref(false);
const syncFromProvider = ref(false);
const autoTop = ref(false);
const startIndex = ref<number | null>(0);
const endIndex = ref<number | null>(65536);
const taskNumber = ref<number | null>(1);

defineExpose({
  getTranslationOptions: () => ({
    translateExpireChapter: translateExpireChapter.value,
    syncFromProvider: syncFromProvider.value,
    startIndex: startIndex.value ?? 0,
    endIndex: endIndex.value ?? 65536,
    taskNumber: taskNumber.value ?? 1,
    autoTop: autoTop.value,
  }),
});

// 下载设置
const showDownloadModal = ref(false);
const tryUseChineseTitleAsFilename = ref(setting.downloadFilenameType === 'zh');
watch(
  tryUseChineseTitleAsFilename,
  (it) => (setting.downloadFilenameType = it ? 'zh' : 'jp')
);
</script>

<template>
  <n-flex vertical>
    <c-action-wrapper title="选项">
      <n-flex>
        <n-checkbox v-model:checked="translateExpireChapter">
          <n-tooltip trigger="hover">
            <template #trigger>过期章节</template>
            翻译术语表过期的章节。
          </n-tooltip>
        </n-checkbox>

        <n-checkbox
          v-if="type === 'web' && userData.passWeek"
          v-model:checked="syncFromProvider"
        >
          <n-tooltip trigger="hover">
            <template #trigger>源站同步</template>
            强行同步已缓存章节，与源站不一致会删除现有翻译，慎用！!
          </n-tooltip>
        </n-checkbox>

        <n-checkbox v-model:checked="autoTop">
          <n-tooltip trigger="hover">
            <template #trigger>排队置顶</template>
            GPT/Sakura任务排队的时候，自动置顶。
          </n-tooltip>
        </n-checkbox>
      </n-flex>
    </c-action-wrapper>

    <c-action-wrapper v-if="type === 'web'" title="范围">
      <!-- "控制翻译任务的范围，章节序号可以看下面目录结尾方括号里的数字。比如，“从0到10”，表示属于区间[0，10)的章节，从第0章到第9章，不包含第10章。均分任务只对排队GPT/Sakura生效，最大为10。" -->
      <n-flex style="text-align: center">
        <div>
          <n-input-group>
            <n-input-group-label size="small">从</n-input-group-label>
            <n-input-number
              size="small"
              v-model:value="startIndex"
              :show-button="false"
              button-placement="both"
              :min="0"
              style="width: 60px"
            />
            <n-input-group-label size="small">到</n-input-group-label>
            <n-input-number
              size="small"
              v-model:value="endIndex"
              :show-button="false"
              :min="0"
              style="width: 60px"
            />
          </n-input-group>
        </div>
        <div>
          <n-input-group>
            <n-input-group-label size="small">均分</n-input-group-label>
            <n-input-number
              size="small"
              v-model:value="taskNumber"
              :show-button="false"
              :min="1"
              :max="10"
              style="width: 60px"
            />
            <n-input-group-label size="small">个任务</n-input-group-label>
          </n-input-group>
        </div>
      </n-flex>
    </c-action-wrapper>

    <c-action-wrapper title="操作">
      <n-button-group size="small">
        <c-button
          :round="false"
          label="下载设置"
          @click="showDownloadModal = true"
        />
        <c-button
          :round="false"
          :label="`编辑术语表[${Object.keys(glossary).length}]`"
          @click="showGlossaryModal = true"
        />
      </n-button-group>
    </c-action-wrapper>

    <c-modal title="编辑术语表" v-model:show="showGlossaryModal">
      <glossary-edit :glossary="glossary" />
      <template #action>
        <c-button
          label="提交"
          async
          require-login
          type="primary"
          @click="submit"
        />
      </template>
    </c-modal>

    <c-modal title="下载设置" v-model:show="showDownloadModal">
      <n-flex vertical size="large">
        <c-action-wrapper title="语言">
          <c-radio-group
            v-model:value="setting.downloadFormat.mode"
            :options="downloadModeOptions"
          />
        </c-action-wrapper>

        <c-action-wrapper title="翻译">
          <n-flex>
            <c-radio-group
              v-model:value="setting.downloadFormat.translationsMode"
              :options="downloadTranslationModeOptions"
            />
            <translator-check
              v-model:value="setting.downloadFormat.translations"
              show-order
              :two-line="!isWideScreen"
            />
          </n-flex>
        </c-action-wrapper>

        <c-action-wrapper v-if="type === 'web'" title="文件">
          <c-radio-group
            v-model:value="setting.downloadFormat.type"
            :options="downloadTypeOptions"
          />
        </c-action-wrapper>

        <c-action-wrapper
          v-if="type === 'web'"
          title="中文文件名"
          align="center"
        >
          <n-switch size="small" v-model:value="tryUseChineseTitleAsFilename" />
        </c-action-wrapper>

        <n-text depth="3" style="font-size: 12px">
          # 某些EPUB阅读器无法正确显示日文段落的浅色字体
        </n-text>
      </n-flex>
    </c-modal>
  </n-flex>
</template>
