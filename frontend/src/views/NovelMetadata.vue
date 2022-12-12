<script lang="ts" setup>
import { onMounted, Ref, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  BookMetadata,
  getNovelMetadata,
  NovelMetadata,
  Result,
  TocChapterToken,
  TocEpisodeToken,
} from '../models/Novel';

enum Mode {
  JP,
  ZH,
}

const route = useRoute();
const modeRef: Ref<Mode> = ref(Mode.JP);
const novelMetadataRef: Ref<Result<NovelMetadata, any> | undefined> = ref();

onMounted(() => {
  getMetadata();
});

async function getMetadata() {
  const providerId = route.params.providerId as string;
  const bookId = route.params.bookId as string;
  const metadata = await getNovelMetadata(providerId, bookId);
  if (metadata.ok && metadata.value.zh !== null) {
    modeRef.value = Mode.ZH;
  } else {
    modeRef.value = Mode.JP;
  }
  console.log(metadata);
  novelMetadataRef.value = metadata;
}
function withMode(metadata: NovelMetadata): BookMetadata {
  if (modeRef.value == Mode.ZH && metadata.zh !== null) {
    return metadata.zh;
  } else {
    return metadata.jp;
  }
}
function instanceOfTocChapterToken(
  object: TocChapterToken | TocEpisodeToken
): object is TocChapterToken {
  return 'level' in object;
}
function instanceOfTocEpisodeToken(
  object: TocChapterToken | TocEpisodeToken
): object is TocEpisodeToken {
  return 'episode_id' in object;
}
</script>

<template>
  <div
    v-if="novelMetadataRef === undefined"
    v-loading="novelMetadataRef === undefined"
  />
  <div v-if="novelMetadataRef !== undefined && novelMetadataRef.ok">
    <h1>
      <a :href="novelMetadataRef.value.url">{{
        withMode(novelMetadataRef.value).title
      }}</a>
    </h1>
    <h2>
      <span v-for="author in withMode(novelMetadataRef.value).authors">
        <a :href="author.link">{{ author.name }}</a>
      </span>
    </h2>
    <p>{{ withMode(novelMetadataRef.value).introduction }}</p>
    <ol>
      <li v-for="token in withMode(novelMetadataRef.value).toc">
        <router-link
          v-if="instanceOfTocEpisodeToken(token)"
          :to="`/novel/${route.params.providerId}/${route.params.bookId}/${token.episode_id}`"
        >
          {{ token.title }}
        </router-link>
      </li>
    </ol>
  </div>
</template>
