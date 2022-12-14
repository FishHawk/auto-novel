<script lang="ts" setup>
import { onMounted, Ref, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  MenuOutlined,
  SettingsOutlined,
  AddOutlined,
  MinusOutlined,
  ArrowBackIosOutlined,
  ArrowForwardIosOutlined,
} from '@vicons/material';

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

const route = useRoute();
const router = useRouter();
const mode = ref(Mode.MIX);
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
        <span>字体大小(未完成)</span>
        <n-button text style="font-size: 24px" @click="">
          <n-icon :depth="3"> <AddOutlined /> </n-icon>
        </n-button>
        <n-button text style="font-size: 24px" @click="">
          <n-icon :depth="3"> <MinusOutlined /> </n-icon>
        </n-button>
      </n-space>
    </n-card>
  </n-modal>

  <div class="content" v-if="episode?.ok" style="margin-bottom: 40px">
    <!-- hacky, prevent margin collapse -->
    <div style="display: inline-block" />

    <n-h3 style="text-align: center; width: 100%">
      {{ episode.value.curr.title }}
      <br />
      <span style="color: gray">{{ episode.value.curr.zh_title }}</span>
    </n-h3>

    <n-space align="center" justify="space-between" style="width: 100%">
      <n-a :href="getPrevEpisodePath()">
        <n-space align="center">
          <n-icon><ArrowBackIosOutlined /></n-icon>
          <span>
            {{ episode.value.prev?.title }}
            <br />
            <span style="color: grey">{{ episode.value.prev?.zh_title }}</span>
          </span>
        </n-space>
      </n-a>

      <n-a :href="`/novel/${route.params.providerId}/${route.params.bookId}`">
        目录
      </n-a>

      <n-a @click="showModal = true"> 设置 </n-a>

      <n-a :href="getNextEpisodePath()">
        <n-space align="center">
          <span style="text-align: end">
            {{ episode.value.next?.title }}
            <br />
            <span style="color: grey">{{ episode.value.next?.zh_title }}</span>
          </span>
          <n-icon><ArrowForwardIosOutlined /></n-icon>
        </n-space>
      </n-a>
    </n-space>

    <n-divider />

    <n-p v-if="!episode.value.translated && mode !== Mode.JP">章节未翻译!</n-p>
    <n-p
      v-if="episode.value.translated || mode === Mode.JP"
      v-for="paragraph in episode.value.paragraphs"
    >
      <n-p>
        {{ mode === Mode.JP ? paragraph.jp : paragraph.zh }}
      </n-p>
      <n-p v-if="mode === Mode.MIX" style="opacity: 0.4">
        {{ paragraph.jp }}
      </n-p>
    </n-p>

    <n-divider />

    <n-space align="center" justify="space-between" style="width: 100%">
      <n-a :href="getPrevEpisodePath()">
        <n-space align="center">
          <n-icon><ArrowBackIosOutlined /></n-icon>
          <span>
            {{ episode.value.prev?.title }}
            <br />
            <span style="color: grey">{{ episode.value.prev?.zh_title }}</span>
          </span>
        </n-space>
      </n-a>
      <n-a :href="getNextEpisodePath()">
        <n-space align="center">
          <span>
            {{ episode.value.next?.title }}
            <br />
            <span style="color: grey">{{ episode.value.next?.zh_title }}</span>
          </span>
          <n-icon><ArrowForwardIosOutlined /></n-icon>
        </n-space>
      </n-a>
    </n-space>
  </div>
</template>
