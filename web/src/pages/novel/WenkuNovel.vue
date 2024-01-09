<script lang="ts" setup>
import { EditNoteFilled } from '@vicons/material';
import { createReusableTemplate } from '@vueuse/core';
import { useMessage, useThemeVars } from 'naive-ui';
import { ref } from 'vue';
import { useRoute } from 'vue-router';

import { ApiWenkuNovel, WenkuNovelDto } from '@/data/api/api_wenku_novel';
import { ResultState } from '@/data/result';
import coverPlaceholder from '@/images/cover_placeholder.png';

import AdvanceOptions from './components/AdvanceOptions.vue';

const [DefineTagGroup, ReuseTagGroup] = createReusableTemplate<{
  label: string;
  tags: string[];
}>();

const message = useMessage();
const vars = useThemeVars();
const route = useRoute();

const novelId = route.params.novelId as string;

const novelMetadataResult = ref<ResultState<WenkuNovelDto>>();

const getNovel = async () => {
  const result = await ApiWenkuNovel.getNovel(novelId);
  if (result.ok) {
    result.value.volumeZh = result.value.volumeZh.sort((a, b) =>
      a.localeCompare(b)
    );
    result.value.volumeJp = result.value.volumeJp.sort((a, b) =>
      a.volumeId.localeCompare(b.volumeId)
    );
  }
  novelMetadataResult.value = result;
  if (result.ok) {
    document.title = result.value.title;
  }
};
getNovel();

const advanceOptions = ref<InstanceType<typeof AdvanceOptions>>();

const submitGlossary = async (glossary: { [key: string]: string }) => {
  const result = await ApiWenkuNovel.updateGlossary(novelId, glossary);
  if (result.ok) {
    message.success('术语表提交成功');
  } else {
    message.error('术语表提交失败：' + result.error.message);
  }
};
</script>

<template>
  <DefineTagGroup v-slot="{ label, tags }">
    <tr v-if="tags.length > 0">
      <td nowrap="nowrap" style="vertical-align: top; padding-right: 12px">
        <n-tag :bordered="false" size="small">
          {{ label }}
        </n-tag>
      </td>
      <td>
        <n-space :size="[4, 4]">
          <n-tag v-for="tag of tags" :bordered="false" size="small">
            {{ tag }}
          </n-tag>
        </n-space>
      </td>
    </tr>
  </DefineTagGroup>

  <div
    v-if="novelMetadataResult?.ok"
    :style="{
      background:
        `linear-gradient(color-mix(in srgb, ${vars.bodyColor} 40%, transparent), ${vars.bodyColor}), ` +
        `url(${novelMetadataResult.value.cover})`,
    }"
    style="
      width: 100%;
      clip: rect(0, auto, auto, 0);
      background-size: cover;
      background-position: center 15%;
    "
  >
    <div style="width: 100%; height: 100%; backdrop-filter: blur(4px)">
      <div class="layout-content">
        <n-space
          :wrap="false"
          style="padding-top: 40px; padding-bottom: 20px; min-height: 260px"
        >
          <n-card size="small" style="width: 160px">
            <template #cover>
              <img
                :src="
                  novelMetadataResult.value.cover
                    ? novelMetadataResult.value.cover
                    : coverPlaceholder
                "
                alt="cover"
              />
            </template>
          </n-card>
          <div>
            <n-h1 prefix="bar" style="font-size: 22px; font-weight: 900">
              {{
                novelMetadataResult.value.titleZh
                  ? novelMetadataResult.value.titleZh
                  : novelMetadataResult.value.title
              }}
              <n-text v-if="novelMetadataResult.value.r18" depth="3">
                [成人]
              </n-text>
            </n-h1>

            <table style="border-spacing: 0px 8px">
              <ReuseTagGroup
                label="作者"
                :tags="novelMetadataResult.value.authors"
              />
              <ReuseTagGroup
                label="插图"
                :tags="novelMetadataResult.value.artists"
              />
            </table>
          </div>
        </n-space>
      </div>
    </div>
  </div>

  <div class="layout-content">
    <ResultView
      :result="novelMetadataResult"
      :showEmpty="() => false"
      v-slot="{ value: metadata }"
      class="layout-content"
    >
      <n-space>
        <RouterNA :to="`/wenku-edit/${novelId}`">
          <n-button>
            <template #icon>
              <n-icon :component="EditNoteFilled" />
            </template>
            编辑
          </n-button>
        </RouterNA>

        <favorite-button
          v-model:favored="metadata.favored"
          :favored-list="metadata.favoredList"
          :novel="{ type: 'wenku', novelId }"
        />

        <n-a
          :href="`https://www.amazon.co.jp/s?k=${metadata.title}&rh=n%3A465392`"
          target="_blank"
        >
          <n-button>在亚马逊搜索</n-button>
        </n-a>
      </n-space>

      <n-p>原名：{{ metadata.title }}</n-p>
      <n-p v-html="metadata.introduction.replace(/\n/g, '<br />')" />

      <n-space :size="[4, 4]">
        <n-tag v-for="tag of metadata.keywords" :bordered="false" size="small">
          {{ tag }}
        </n-tag>
      </n-space>

      <SectionHeader v-if="metadata.volumes.length" title="各卷封面" />
      <div v-if="metadata.volumes.length">
        <n-scrollbar x-scrollable>
          <div style="white-space: nowrap">
            <n-card
              v-for="volume of metadata.volumes"
              size="small"
              header-style="padding: 8px;"
              :bordered="false"
              :wrap="false"
              style="
                display: inline-block;
                width: 100px;
                margin: 4px;
                padding-bottom: 12px;
              "
            >
              <template #cover>
                <img
                  :src="volume.cover"
                  alt="cover"
                  style="aspect-ratio: 1 / 1.5; object-fit: cover"
                />
              </template>
            </n-card>
          </div>
        </n-scrollbar>
      </div>

      <SectionHeader title="中文章节" />
      <UploadButton type="zh" :novelId="novelId" @uploadFinished="getNovel()" />
      <n-ul>
        <n-li v-for="volumeId in metadata.volumeZh">
          <n-a
            :href="`/files-wenku/${novelId}/${encodeURIComponent(volumeId)}`"
            target="_blank"
            :download="volumeId"
          >
            {{ volumeId }}
          </n-a>
        </n-li>
      </n-ul>

      <SectionHeader title="日文章节" />
      <UploadButton type="jp" :novelId="novelId" @uploadFinished="getNovel()" />

      <advance-options
        ref="advanceOptions"
        type="wenku"
        :glossary="metadata.glossary"
        :submit="() => submitGlossary(metadata.glossary)"
      />

      <n-list>
        <template v-for="volume of metadata.volumeJp">
          <n-list-item>
            <WenkuVolume
              :novel-id="novelId"
              :volume="volume"
              :get-params="() => advanceOptions!!.getTranslationOptions()"
              @deleted="getNovel()"
            />
          </n-list-item>
        </template>
      </n-list>

      <CommentList :site="`wenku-${novelId}`" />
    </ResultView>
  </div>
</template>
