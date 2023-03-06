<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { ResultState } from '../data/api/result';
import ApiPatch, { BookPatchDto } from '../data/api/api_patch';
import TextDiff from '../components/TextDiff.vue';
import { buildMetadataUrl } from '../data/provider';

const route = useRoute();
const providerId = route.params.providerId as string;
const bookId = route.params.bookId as string;
const url = buildMetadataUrl(providerId, bookId);

const bookPatch = ref<ResultState<BookPatchDto>>();
onMounted(() => getMetadata());
async function getMetadata() {
  const result = await ApiPatch.getPatch(providerId, bookId);
  bookPatch.value = result;
  if (result.ok) {
    document.title = `修改记录 - ${result.value.titleJp}`;
  }
}
</script>

<template>
  <div class="content">
    <div v-if="bookPatch?.ok">
      <n-h2 style="text-align: center; width: 100%">
        <n-a :href="url" target="_blank">{{ bookPatch.value.titleJp }}</n-a>
        <br />
        <span style="color: grey">{{ bookPatch.value.titleZh }}</span>
      </n-h2>

      <n-space align="center" justify="space-around">
        <n-a href="/">
          <n-button text>
            <template #icon>
              <n-icon> <SearchFilled /> </n-icon>
            </template>
            搜索
          </n-button>
        </n-a>
        <n-a href="/list">
          <n-button text>
            <template #icon>
              <n-icon> <FormatListBulletedFilled /> </n-icon>
            </template>
            列表
          </n-button>
        </n-a>
      </n-space>

      <n-divider />

      <div v-for="metadataPatch of bookPatch.value.patches">
        <n-h4 prefix="bar">{{ metadataPatch.uuid }}</n-h4>
        <n-space vertical>
          <TextDiff
            v-if="metadataPatch.titleChange"
            :diff="metadataPatch.titleChange"
          />
          <TextDiff
            v-if="metadataPatch.introductionChange"
            :diff="metadataPatch.introductionChange"
          />
          <TextDiff
            v-for="textChange of metadataPatch.tocChange"
            :diff="textChange"
          />

          <table style="border-spacing: 16px 0px">
            <tr v-for="(termZh, termJp) in metadataPatch.glossary">
              <td>{{ termJp }}</td>
              <td style="width: 4px">=></td>
              <td>{{ termZh }}</td>
            </tr>
          </table>
        </n-space>
      </div>

      <div
        v-for="[episodeId, episodePatches] of Object.entries(
          bookPatch.value.toc
        )"
      >
        <n-h2 prefix="bar">{{ episodePatches.titleJp }}</n-h2>
        <div v-for="episodePatch in episodePatches.patches">
          <n-h4 prefix="bar">{{ episodePatch.uuid }}</n-h4>
          <n-space vertical>
            <TextDiff
              v-for="textChange of episodePatch.paragraphsChange"
              :diff="textChange"
            />
          </n-space>
          <n-divider />
        </div>
      </div>
    </div>
  </div>
</template>
