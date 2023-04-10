<script lang="ts" setup>
import { onMounted, ref, shallowRef } from 'vue';
import { useRoute } from 'vue-router';
import { NConfigProvider, lightTheme, darkTheme, useMessage } from 'naive-ui';

import { ResultState } from '../data/api/result';
import ApiWebNovel, { BookEpisodeDto } from '../data/api/api_web_novel';
import { useAuthInfoStore } from '../data/stores/authInfo';
import { useReaderSettingStore } from '../data/stores/readerSetting';
import { buildEpisodeUrl } from '../data/provider';

const authInfoStore = useAuthInfoStore();
const readerSettingStore = useReaderSettingStore();

const message = useMessage();

const route = useRoute();
const providerId = route.params.providerId as string;
const bookId = route.params.bookId as string;
const episodeId = route.params.episodeId as string;
const url = buildEpisodeUrl(providerId, bookId, episodeId);

const modeOptions = [
  { value: 'jp', label: '日文' },
  { value: 'zh', label: '中文' },
  { value: 'mix', label: '中日混合' },
];
const translationOptions = [
  { value: 'youdao', label: '有道' },
  { value: 'baidu', label: '百度' },
  { value: 'compare', label: '对比（测试用）' },
];
const fontSizeOptions = ['14px', '16px', '18px', '20px'];
const themeOptions = [
  { isDark: false, bodyColor: '#FFFFFF' },
  { isDark: false, bodyColor: '#FFF2E2' },
  { isDark: false, bodyColor: '#E3EDCD' },
  { isDark: false, bodyColor: '#E9EBFE' },
  { isDark: false, bodyColor: '#EAEAEF' },

  { isDark: true, bodyColor: '#000000' },
  { isDark: true, bodyColor: '#272727' },
];

const showModal = ref(false);

const bookEpisode = shallowRef<ResultState<BookEpisodeDto>>();
onMounted(() => getEpisode());
async function getEpisode() {
  const result = await ApiWebNovel.getEpisode(providerId, bookId, episodeId);
  bookEpisode.value = result;
  if (result.ok) {
    document.title = result.value.titleJp;
  }
}

function getTextList(
  episode: BookEpisodeDto
): { text1: string; text2?: string; textCompare?: string }[] {
  const paragraphsJp = episode.paragraphs;

  const paragraphsZh =
    readerSettingStore.translation === 'youdao'
      ? episode.youdaoParagraphs
      : episode.baiduParagraphs;

  if (readerSettingStore.mode == 'jp') {
    return paragraphsJp.map((text) => {
      return { text1: text };
    });
  } else if (paragraphsZh) {
    if (readerSettingStore.mode == 'zh') {
      return paragraphsZh.map((text) => {
        return { text1: text };
      });
    } else {
      return paragraphsJp.map(function (textJp, i) {
        const textZh = paragraphsZh[i];
        const textCompare =
          readerSettingStore.translation !== 'compare'
            ? undefined
            : (episode.youdaoParagraphs ?? [])[i] ?? '';
        return {
          text1: textZh,
          text2: textJp,
          textCompare,
        };
      });
    }
  } else {
    return [{ text1: '章节未翻译!' }];
  }
}

const editMode = ref(false);
function enableEditMode() {
  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }
  editMode.value = true;
}
</script>

<template>
  <n-config-provider
    :theme="readerSettingStore.theme.isDark ? darkTheme : lightTheme"
    :theme-overrides="{
      common: { bodyColor: readerSettingStore.theme.bodyColor },
      Pagination: { itemColorDisabled: '#00000' },
    }"
  >
    <n-global-style />

    <n-modal v-model:show="showModal">
      <n-card
        style="width: min(600px, calc(100% - 16px))"
        title="设置"
        :bordered="false"
        size="huge"
        role="dialog"
        aria-modal="true"
      >
        <table style="border-spacing: 0px 16px">
          <LabelTr label="语言">
            <n-radio-group v-model:value="readerSettingStore.mode" name="mode">
              <n-space>
                <n-radio
                  v-for="option in modeOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </n-radio>
              </n-space>
            </n-radio-group>
          </LabelTr>

          <LabelTr label="翻译">
            <n-radio-group
              v-model:value="readerSettingStore.translation"
              name="translator"
            >
              <n-space>
                <n-radio
                  v-for="option in translationOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </n-radio>
              </n-space>
            </n-radio-group>
          </LabelTr>

          <LabelTr label="字体">
            <n-radio-group
              v-model:value="readerSettingStore.fontSize"
              name="fontSize"
            >
              <n-space>
                <n-radio
                  v-for="option in fontSizeOptions"
                  :key="option"
                  :value="option"
                >
                  {{ option }}
                </n-radio>
              </n-space>
            </n-radio-group>
          </LabelTr>

          <LabelTr label="主题">
            <n-space>
              <n-radio
                v-for="theme of themeOptions"
                :checked="theme.bodyColor == readerSettingStore.theme.bodyColor"
                @update:checked="readerSettingStore.theme = theme"
              >
                <n-tag
                  :color="{
                    color: theme.bodyColor,
                    textColor: theme.isDark ? 'white' : 'black',
                  }"
                  style="width: 8em"
                >
                  {{ theme.bodyColor }}
                </n-tag>
              </n-radio>
            </n-space>
          </LabelTr>
        </table>
      </n-card>
    </n-modal>

    <div class="content" v-if="bookEpisode?.ok">
      <n-h2 style="text-align: center; width: 100%">
        <n-a :href="url" target="_blank">{{ bookEpisode.value.titleJp }}</n-a>
        <br />
        <span style="color: gray">{{ bookEpisode.value.titleZh }}</span>
      </n-h2>

      <n-space align="center" justify="space-between" style="width: 100%">
        <n-a
          v-if="bookEpisode.value.prevId"
          :href="`/novel/${providerId}/${bookId}/${bookEpisode.value.prevId}`"
          >上一章</n-a
        >
        <n-text v-else style="color: grey">上一章</n-text>

        <n-a :href="`/novel/${providerId}/${bookId}`">目录</n-a>
        <n-a @click="showModal = true">设置</n-a>

        <!-- <n-text v-if="!bookEpisode.value.paragraphsZh" style="color: grey">
          编辑
        </n-text>
        <n-a v-else-if="!editMode" @click="enableEditMode()"> 编辑 </n-a>
        <n-a v-else @click="editMode = false"> 返回 </n-a> -->

        <n-a
          v-if="bookEpisode.value.nextId"
          :href="`/novel/${providerId}/${bookId}/${bookEpisode.value.nextId}`"
          >下一章</n-a
        >
        <n-text v-else style="color: grey">下一章</n-text>
      </n-space>

      <n-divider />

      <EditEpisodeSection
        v-if="editMode"
        :provider-id="providerId"
        :book-id="bookId"
        :episode-id="episodeId"
        v-model:book-episode="bookEpisode.value"
      />

      <template v-else>
        <div id="episode-content">
          <template v-for="text in getTextList(bookEpisode.value)">
            <n-p v-if="text.text1.trim().length === 0">
              <br />
            </n-p>
            <template v-if="text.text1.trim().length > 0">
              <n-p :style="{ fontSize: readerSettingStore.fontSize }">
                {{ text.text1 }}
              </n-p>
              <n-p
                v-if="text.textCompare"
                :style="{ fontSize: readerSettingStore.fontSize }"
              >
                {{ text.textCompare }}
              </n-p>
              <n-p
                v-if="text.text2"
                :style="{
                  fontSize: readerSettingStore.fontSize,
                  opacity: 0.4,
                }"
              >
                {{ text.text2 }}
              </n-p>
            </template>
          </template>
        </div>

        <n-divider />

        <n-space align="center" justify="space-between" style="width: 100%">
          <n-a
            v-if="bookEpisode.value.prevId"
            :href="`/novel/${providerId}/${bookId}/${bookEpisode.value.prevId}`"
            >上一章</n-a
          >
          <n-text v-else style="color: grey">上一章</n-text>

          <n-a
            v-if="bookEpisode.value.nextId"
            :href="`/novel/${providerId}/${bookId}/${bookEpisode.value.nextId}`"
            >下一章</n-a
          >
          <n-text v-else style="color: grey">下一章</n-text>
        </n-space>
      </template>
    </div>

    <div v-if="bookEpisode && !bookEpisode.ok">
      <n-result
        status="error"
        title="加载错误"
        :description="bookEpisode.error.message"
      />
    </div>
  </n-config-provider>
</template>

<style scoped>
#episode-content p {
  word-wrap: break-word;
}
.content {
  width: 800px;
  margin: 0 auto;
  padding-left: 24px;
  padding-right: 24px;
  padding-bottom: 48px;
}
@media only screen and (max-width: 600px) {
  .content {
    width: auto;
    padding-left: 10px;
    padding-right: 10px;
    padding-bottom: 48px;
  }
}
</style>
