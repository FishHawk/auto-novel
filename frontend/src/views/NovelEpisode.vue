<script lang="ts" setup>
import { onMounted, Ref, ref } from 'vue';
import { useRoute } from 'vue-router';
import { getNovelEpisode, NovelEpisode, Result } from '../models/Novel';

enum Mode {
  JP,
  ZH,
  MIX,
}

const route = useRoute();
const modeRef: Ref<Mode> = ref(Mode.JP);
const novelEpisodeRef: Ref<Result<NovelEpisode, any> | undefined> = ref();

onMounted(() => {
  getEpisode();
});

async function getEpisode() {
  const providerId = route.params.providerId as string;
  const bookId = route.params.bookId as string;
  const episodeId = route.params.episodeId as string;
  const episode = await getNovelEpisode(providerId, bookId, episodeId);
  if (episode.ok && episode.value.zh !== null) {
    modeRef.value = Mode.ZH;
  } else {
    modeRef.value = Mode.JP;
  }
  novelEpisodeRef.value = episode;
}

interface Paragraph {
  content: string;
  primary: boolean;
}

function withMode(episode: NovelEpisode): Paragraph[] {
  if (modeRef.value !== Mode.JP && episode.zh !== null) {
    if (modeRef.value == Mode.ZH) {
      return episode.zh.map((it) => {
        return { content: it, primary: true };
      });
    } else {
      const result = [];
      for (let i = 0; i < episode.zh.length; i++) {
        result.push({ content: episode.zh[i], primary: true });
        result.push({ content: episode.jp[i], primary: false });
      }
      return result;
    }
  } else {
    return episode.jp.map((it) => {
      return { content: it, primary: true };
    });
  }
}
</script>

<template>
  <div
    v-if="novelEpisodeRef === undefined"
    v-loading="novelEpisodeRef === undefined"
  />
  <div v-if="novelEpisodeRef !== undefined && novelEpisodeRef.ok">
    <div>
      <h1>title placeholder</h1>
      <h3>sub title placeholder</h3>
    </div>
    <div>
      <p v-for="content in withMode(novelEpisodeRef.value)">
        {{ content.content }}
      </p>
    </div>
  </div>
</template>
