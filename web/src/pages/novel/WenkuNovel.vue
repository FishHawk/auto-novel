<script lang="ts" setup>
import { EditNoteOutlined } from '@vicons/material';
import { createReusableTemplate } from '@vueuse/core';
import { useMessage, useThemeVars } from 'naive-ui';
import { ref } from 'vue';
import { useRoute } from 'vue-router';

import { ApiWenkuNovel, WenkuNovelDto } from '@/data/api/api_wenku_novel';
import { Result } from '@/data/result';
import { useUserDataStore } from '@/data/stores/user_data';
import coverPlaceholder from '@/images/cover_placeholder.png';

import AdvanceOptions from './components/AdvanceOptions.vue';
import { useIsWideScreen } from '@/data/util';

const [DefineTagGroup, ReuseTagGroup] = createReusableTemplate<{
  label: string;
  tags: string[];
}>();

const isWideScreen = useIsWideScreen(600);
const message = useMessage();
const userData = useUserDataStore();
const vars = useThemeVars();
const route = useRoute();

const novelId = route.params.novelId as string;

const novelMetadataResult = ref<Result<WenkuNovelDto>>();

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

const deleteVolume = async (volumeId: string) => {
  const result = await ApiWenkuNovel.deleteVolume(novelId, volumeId);
  if (result.ok) {
    getNovel();
    message.info('删除成功');
  } else {
    message.error('删除失败：' + result.error.message);
  }
};
</script>

<template>
  <DefineTagGroup v-slot="{ label, tags }">
    <n-flex v-if="tags.length > 0" :warp="false">
      <n-tag :bordered="false" size="small">
        {{ label }}
      </n-tag>
      <n-flex :size="[4, 4]">
        <n-tag v-for="tag of tags" :bordered="false" size="small">
          {{ tag }}
        </n-tag>
      </n-flex>
    </n-flex>
  </DefineTagGroup>

  <div
    v-if="novelMetadataResult?.ok"
    :style="{
      background: `url(${novelMetadataResult.value.cover})`,
    }"
    style="
      width: 100%;
      clip: rect(0, auto, auto, 0);
      background-size: cover;
      background-position: center 15%;
    "
  >
    <div
      :style="{
        background: `linear-gradient(to bottom, ${
          vars.bodyColor == '#fff' ? '#ffffff80' : 'rgba(16, 16, 20, 0.5)'
        }, ${vars.bodyColor})`,
      }"
      style="width: 100%; height: 100%; backdrop-filter: blur(8px)"
    >
      <div class="layout-content">
        <n-flex :wrap="false" style="padding-top: 20px; padding-bottom: 21px">
          <div>
            <n-image
              width="160"
              :src="
                novelMetadataResult.value.cover
                  ? novelMetadataResult.value.cover
                  : coverPlaceholder
              "
              alt="cover"
              show-toolbar-tooltip
              style="border-radius: 2px"
            />
          </div>
          <n-flex vertical>
            <n-h2
              prefix="bar"
              style="margin: 8px 0"
              :style="{ 'font-size': isWideScreen ? '22px' : '18px' }"
            >
              <b>
                {{
                  novelMetadataResult.value.titleZh
                    ? novelMetadataResult.value.titleZh
                    : novelMetadataResult.value.title
                }}
                <n-text v-if="novelMetadataResult.value.r18" depth="3">
                  [成人]
                </n-text>
              </b>
            </n-h2>

            <ReuseTagGroup
              label="作者"
              :tags="novelMetadataResult.value.authors"
            />
            <ReuseTagGroup
              label="插图"
              :tags="novelMetadataResult.value.artists"
            />
          </n-flex>
        </n-flex>
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
      <n-flex>
        <router-link :to="`/wenku-edit/${novelId}`">
          <c-button label="编辑" :icon="EditNoteOutlined" />
        </router-link>

        <favorite-button
          v-model:favored="metadata.favored"
          :favored-list="metadata.favoredList"
          :novel="{ type: 'wenku', novelId }"
        />

        <c-button
          label="在亚马逊搜索"
          tag="a"
          :href="`https://www.amazon.co.jp/s?k=${metadata.title}&rh=n%3A465392`"
          target="_blank"
        />
      </n-flex>

      <n-p>原名：{{ metadata.title }}</n-p>
      <n-p v-html="metadata.introduction.replace(/\n/g, '<br />')" />

      <n-flex :size="[4, 4]">
        <router-link
          v-for="keyword of metadata.keywords"
          :to="`/wenku-list?query=${keyword}\$`"
        >
          <novel-tag :tag="keyword" />
        </router-link>
      </n-flex>

      <template v-if="metadata.volumes.length">
        <section-header title="各卷封面" />
        <n-scrollbar x-scrollable>
          <n-image-group show-toolbar-tooltip>
            <n-flex :size="4" :wrap="false" style="margin-bottom: 16px">
              <n-image
                v-for="volume of metadata.volumes"
                width="104"
                :src="volume.cover"
                :alt="volume.asin"
                lazy
                style="border-radius: 2px"
              />
            </n-flex>
          </n-image-group>
        </n-scrollbar>
      </template>

      <section-header title="中文章节" />
      <upload-button
        type="zh"
        :novel-id="novelId"
        @upload-finished="getNovel()"
      />

      <n-ul>
        <n-li v-for="volumeId in metadata.volumeZh" :key="volumeId">
          <n-a
            :href="`/files-wenku/${novelId}/${encodeURIComponent(volumeId)}`"
            target="_blank"
            :download="volumeId"
          >
            {{ volumeId }}
          </n-a>

          <n-popconfirm
            v-if="userData.isMaintainer"
            :show-icon="false"
            @positive-click="deleteVolume(volumeId)"
            :negative-text="null"
          >
            <template #trigger>
              <n-button text type="error" style="margin-left: 16px">
                删除
              </n-button>
            </template>
            真的要删除《{{ volumeId }}》吗？
          </n-popconfirm>
        </n-li>
      </n-ul>

      <section-header title="日文章节" />
      <upload-button
        type="jp"
        :novel-id="novelId"
        @upload-finished="getNovel()"
      />

      <advance-options
        ref="advanceOptions"
        type="wenku"
        :glossary="metadata.glossary"
        :submit="() => submitGlossary(metadata.glossary)"
      />

      <n-list>
        <n-list-item v-for="volume of metadata.volumeJp" :key="volume.volumeId">
          <WenkuVolume
            :novel-id="novelId"
            :volume="volume"
            :get-params="() => advanceOptions!!.getTranslationOptions()"
            @delete="deleteVolume(volume.volumeId)"
          />
        </n-list-item>
      </n-list>

      <CommentList :site="`wenku-${novelId}`" />
    </ResultView>
  </div>
</template>
