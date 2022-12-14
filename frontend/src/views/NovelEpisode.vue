<script lang="ts" setup>
import { onMounted, Ref, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { AddOutlined, MinusOutlined } from '@vicons/material';

import { Result } from '../models/util';
import { ContentEpisode, getContentEpisode } from '../models/book_content';

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
const showModal = ref(false);
const episode: Ref<Result<ContentEpisode, any> | undefined> = ref();

onMounted(() => {
  getEpisode();
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
  const providerId = route.params.providerId as string;
  const bookId = route.params.bookId as string;
  const episodeId = route.params.episodeId as string;
  const contentEpisode = await getContentEpisode(providerId, bookId, episodeId);
  episode.value = contentEpisode;
}

function getEpisodePath(episodeId: string): string {
  const providerId = route.params.providerId as string;
  const bookId = route.params.bookId as string;
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
    </n-card>
  </n-modal>

  <div class="content" v-if="episode?.ok">
    <n-h3 style="text-align: center; width: 100%">
      {{ episode.value.curr.title }}
      <br />
      <span style="color: gray">{{ episode.value.curr.zh_title }}</span>
    </n-h3>

    <n-space align="center" justify="space-between" style="width: 100%">
      <n-a v-if="episode.value.prev" :href="getPrevEpisodePath()">上一章</n-a>
      <n-text v-if="!episode.value.prev" style="color: grey">上一章</n-text>
      <n-a :href="`/novel/${route.params.providerId}/${route.params.bookId}`">
        目录
      </n-a>
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
</template>
