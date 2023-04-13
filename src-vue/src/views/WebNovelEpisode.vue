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
const setting = useReaderSettingStore();

const message = useMessage();

const route = useRoute();
const providerId = route.params.providerId as string;
const bookId = route.params.bookId as string;
const episodeId = route.params.episodeId as string;
const url = buildEpisodeUrl(providerId, bookId, episodeId);

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
): { text1: string; text2?: string }[] {
  const paragraphsJp = episode.paragraphs;

  const paragraphsZh =
    setting.translation === 'youdao'
      ? episode.youdaoParagraphs
      : episode.baiduParagraphs;

  if (setting.mode == 'jp') {
    return paragraphsJp.map((text) => {
      return { text1: text };
    });
  } else if (paragraphsZh) {
    if (setting.mode == 'zh') {
      return paragraphsZh.map((text) => {
        return { text1: text };
      });
    } else {
      return paragraphsJp.map(function (textJp, i) {
        const textZh = paragraphsZh[i];
        return {
          text1: textZh,
          text2: textJp,
        };
      });
    }
  } else {
    if (setting.translation === 'youdao') {
      return [{ text1: '有道翻译版本不存在' }];
    } else {
      return [{ text1: '百度翻译版本不存在' }];
    }
  }
}

function getCompareTextList(
  episode: BookEpisodeDto
): { textBaidu: string; textYoudao: string; textJp: string }[] {
  return [];
}

// const editMode = ref(false);
// function enableEditMode() {
//   const token = authInfoStore.token;
//   if (!token) {
//     message.info('请先登录');
//     return;
//   }
//   editMode.value = true;
// }
</script>

<template>
  <n-config-provider
    :theme="setting.theme.isDark ? darkTheme : lightTheme"
    :theme-overrides="{
      common: { bodyColor: setting.theme.bodyColor },
      Pagination: { itemColorDisabled: '#00000' },
    }"
  >
    <n-global-style />

    <ReaderSettingDialog v-model:show="showModal" />

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

      <!-- <EditEpisodeSection
        v-if="editMode"
        :provider-id="providerId"
        :book-id="bookId"
        :episode-id="episodeId"
        v-model:book-episode="bookEpisode.value"
      /> -->

      <div id="episode-content">
        <template v-for="text in getTextList(bookEpisode.value)">
          <br v-if="text.text1.trim().length === 0" />
          <n-p
            v-if="text.text2 && setting.mode === 'mix-reverse'"
            class="secondary"
          >
            {{ text.text2 }}
          </n-p>
          <n-p>{{ text.text1 }}</n-p>
          <n-p v-if="text.text2 && setting.mode === 'mix'" class="secondary">
            {{ text.text2 }}
          </n-p>
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
  font-size: v-bind('setting.fontSize');
}
#episode-content .secondary {
  opacity: v-bind('setting.mixJpOpacity');
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
