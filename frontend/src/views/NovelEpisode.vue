<script lang="ts" setup>
import { onMounted, Ref, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { Result } from '../models/util';
import { ContentEpisode, getContentEpisode } from '../models/book_content';
import { buildEpisodeUrl } from '../models/provider';

import { NConfigProvider, lightTheme, darkTheme } from 'naive-ui';

interface Theme {
  isDark: boolean;
  bodyColor: string;
}
const themeOptions: Theme[] = [
  { isDark: false, bodyColor: '#FFFFFF' },
  { isDark: false, bodyColor: '#FFF2E2' },
  { isDark: false, bodyColor: '#E3EDCD' },
  { isDark: false, bodyColor: '#E9EBFE' },
  { isDark: false, bodyColor: '#EAEAEF' },

  { isDark: true, bodyColor: '#000000' },
  { isDark: true, bodyColor: '#272727' },
];
const themeIndex = ref(0);

enum Mode {
  JP,
  ZH,
  MIX,
}
const modeOptions = [
  { value: Mode.JP, label: '日文' },
  { value: Mode.ZH, label: '中文' },
  { value: Mode.MIX, label: '中日混合' },
];
const mode = ref(Mode.MIX);

const fontSizeOptions = ['14px', '16px', '18px', '20px'];
const fontSize = ref('14px');

const route = useRoute();
const providerId = route.params.providerId as string;
const bookId = route.params.bookId as string;
const episodeId = route.params.episodeId as string;
const url = buildEpisodeUrl(providerId, bookId, episodeId);

const showModal = ref(false);
const episode: Ref<Result<ContentEpisode, any> | undefined> = ref();

onMounted(() => {
  getEpisode();

  const themeIndexRaw = localStorage.getItem('episode-theme-index');
  if (themeIndexRaw !== null) {
    themeIndex.value = +themeIndexRaw;
  }

  const modeRaw = localStorage.getItem('episode-mode');
  for (const option of modeOptions) {
    if (modeRaw === option.label) {
      mode.value = option.value;
      break;
    }
  }

  const fontSizeRaw = localStorage.getItem('episode-font-size');
  for (const option of fontSizeOptions) {
    if (fontSizeRaw === option) {
      fontSize.value = option;
      break;
    }
  }
});

async function getEpisode() {
  const contentEpisode = await getContentEpisode(providerId, bookId, episodeId);
  episode.value = contentEpisode;
}

function getEpisodePath(episodeId: string): string {
  return `/novel/${providerId}/${bookId}/${episodeId}`;
}

function getPrevEpisodePath(): string {
  if (episode.value?.ok) {
    const episodeId = episode.value.value.prev?.episode_id;
    if (episodeId) {
      return getEpisodePath(episodeId);
    }
  }
  return '';
}

function getNextEpisodePath(): string {
  if (episode.value?.ok) {
    const episodeId = episode.value.value.next?.episode_id;
    if (episodeId) {
      return getEpisodePath(episodeId);
    }
  }
  return '';
}

watch(themeIndex, (themeIndex) => {
  localStorage.setItem('episode-theme-index', themeIndex.toString());
});
watch(mode, (mode) => {
  for (const option of modeOptions) {
    if (mode === option.value) {
      localStorage.setItem('episode-mode', option.label);
      break;
    }
  }
});
watch(fontSize, (fontSize) => {
  for (const option of fontSizeOptions) {
    if (fontSize === option) {
      localStorage.setItem('episode-font-size', option);
      break;
    }
  }
});
</script>

<template>
  <n-config-provider
    :theme="themeOptions[themeIndex].isDark ? darkTheme : lightTheme"
    :theme-overrides="{
      common: { bodyColor: themeOptions[themeIndex].bodyColor },
    }"
  >
    <n-modal v-model:show="showModal">
      <n-card
        style="width: 600px"
        title="设置"
        :bordered="false"
        size="huge"
        role="dialog"
        aria-modal="true"
      >
        <n-space>
          <span>语言</span>
          <n-radio-group v-model:value="mode" name="mode">
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
        </n-space>

        <n-space style="margin-top: 15px">
          <span>字体大小</span>
          <n-radio-group v-model:value="fontSize" name="fontSize">
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
        </n-space>

        <n-space style="margin-top: 15px">
          <span>主题</span>
          <n-radio-group v-model:value="themeIndex" name="fontSize">
            <n-space>
              <n-radio
                v-for="index in themeOptions.length"
                :key="index"
                :value="index"
              >
                <n-tag
                  :bordered="false"
                  :color="{
                    color: themeOptions[index].bodyColor,
                    textColor: themeOptions[index].isDark ? 'white' : 'black',
                  }"
                  style="width: 8em"
                >
                  {{ themeOptions[index].bodyColor }}
                </n-tag>
              </n-radio>
            </n-space>
          </n-radio-group>
        </n-space>
      </n-card>
    </n-modal>

    <div class="content" v-if="episode?.ok">
      <n-h2 style="text-align: center; width: 100%">
        <n-a :href="url" target="_blank">{{ episode.value.curr.title }}</n-a>
        <br />
        <span style="color: gray">{{ episode.value.curr.zh_title }}</span>
      </n-h2>

      <n-space align="center" justify="space-between" style="width: 100%">
        <n-a v-if="episode.value.prev" :href="getPrevEpisodePath()">上一章</n-a>
        <n-text v-if="!episode.value.prev" style="color: grey">上一章</n-text>
        <n-a :href="`/novel/${providerId}/${bookId}`"> 目录 </n-a>
        <n-a @click="showModal = true"> 设置 </n-a>
        <n-a v-if="episode.value.next" :href="getNextEpisodePath()">下一章</n-a>
        <n-text v-if="!episode.value.next" style="color: grey">下一章</n-text>
      </n-space>

      <n-divider />

      <n-p
        v-if="!episode.value.translated && mode !== Mode.JP"
        :style="{ fontSize: fontSize }"
      >
        章节未翻译!
      </n-p>
      <template
        v-if="episode.value.translated || mode === Mode.JP"
        v-for="paragraph in episode.value.paragraphs"
      >
        <n-p :style="{ fontSize: fontSize }">
          {{ mode === Mode.JP ? paragraph.jp : paragraph.zh }}
        </n-p>
        <n-p
          v-if="mode === Mode.MIX"
          :style="{ fontSize: fontSize }"
          style="color: grey"
        >
          {{ paragraph.jp }}
        </n-p>
      </template>

      <n-divider />

      <n-space align="center" justify="space-between" style="width: 100%">
        <n-a v-if="episode.value.prev" :href="getPrevEpisodePath()">上一章</n-a>
        <n-text v-if="!episode.value.prev" style="color: grey">上一章</n-text>
        <n-a v-if="episode.value.next" :href="getNextEpisodePath()">下一章</n-a>
        <n-text v-if="!episode.value.next" style="color: grey">下一章</n-text>
      </n-space>
    </div>

    <n-global-style />
  </n-config-provider>
</template>
