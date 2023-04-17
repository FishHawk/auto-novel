<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useMessage } from 'naive-ui';

import { ResultState } from '../data/api/result';
import ApiPatch, { BookPatchDto } from '../data/api/api_patch';
import TextDiff from '../components/TextDiff.vue';
import { useAuthInfoStore } from '../data/stores/authInfo';

const message = useMessage();

const route = useRoute();
const providerId = route.params.providerId as string;
const bookId = route.params.bookId as string;

const bookPatch = ref<ResultState<BookPatchDto>>();
onMounted(() => getMetadata());
async function getMetadata() {
  const result = await ApiPatch.getPatch(providerId, bookId);
  bookPatch.value = result;
  if (result.ok) {
    document.title = `修改历史 - ${result.value.titleJp}`;
  }
}

const auth = useAuthInfoStore();

async function deletePatch() {
  const result = await ApiPatch.deletePatch(providerId, bookId, auth.token);
  if (result.ok) {
    message.info('删除成功');
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

async function revokePatch() {
  message.info('暂未实现');
}
</script>

<template>
  <MainLayout>
    <div v-if="bookPatch?.ok">
      <n-h2 prefix="bar">
        <n-a :href="`/novel/${providerId}/${bookId}`" target="_blank">{{
          bookPatch.value.titleJp
        }}</n-a>
        <br />
        <span style="color: grey">{{ bookPatch.value.titleZh }}</span>
      </n-h2>

      <n-space>
        <n-button @click="deletePatch()">删除</n-button>
        <n-button @click="revokePatch()">撤销</n-button>
      </n-space>

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
  </MainLayout>
</template>
