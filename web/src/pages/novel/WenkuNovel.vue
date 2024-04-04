<script lang="ts" setup>
import { EditNoteOutlined, LanguageOutlined } from '@vicons/material';
import { createReusableTemplate } from '@vueuse/core';

import { Locator } from '@/data';
import { WenkuNovelRepository } from '@/data/api';
import coverPlaceholder from '@/image/cover_placeholder.png';
import { GenericNovelId } from '@/model/Common';
import { WenkuNovelDto } from '@/model/WenkuNovel';
import { Result, runCatching } from '@/util/result';
import { doAction, useIsWideScreen } from '@/pages/util';

import TranslateOptions from './components/TranslateOptions.vue';

const [DefineTagGroup, ReuseTagGroup] = createReusableTemplate<{
  label: string;
  tags: string[];
}>();

const isWideScreen = useIsWideScreen(600);
const message = useMessage();
const vars = useThemeVars();
const route = useRoute();

const { atLeastMaintainer } = Locator.userDataRepository();

const novelId = route.params.novelId as string;

const novelMetadataResult = ref<Result<WenkuNovelDto>>();

const getNovel = async () => {
  const result = await runCatching(WenkuNovelRepository.getNovel(novelId));
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

const translateOptions = ref<InstanceType<typeof TranslateOptions>>();

const deleteVolume = (volumeId: string) =>
  doAction(
    WenkuNovelRepository.deleteVolume(novelId, volumeId).then(() => {
      getNovel();
    }),
    '删除',
    message
  );

const buildSearchLink = (tag: string) => `/wenku-list?query="${tag}"`;

const showWebNovelsModal = ref(false);
</script>

<template>
  <DefineTagGroup v-slot="{ label, tags }">
    <n-flex v-if="tags.length > 0" :wrap="false">
      <n-tag :bordered="false" size="small">
        {{ label }}
      </n-tag>
      <n-flex :size="[4, 4]">
        <router-link v-for="tag of tags" :to="buildSearchLink(tag)">
          <novel-tag :tag="tag" />
        </router-link>
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
    <c-result :result="novelMetadataResult" v-slot="{ value: metadata }">
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
          v-if="metadata.webIds.length > 0"
          label="网络"
          :icon="LanguageOutlined"
          @action="showWebNovelsModal = true"
        />

        <c-modal
          title="相关网络小说"
          v-model:show="showWebNovelsModal"
          :extra-height="100"
        >
          <n-ul>
            <n-li v-for="webId of metadata.webIds">
              <c-a :to="`/novel/${webId}`">
                {{ webId }}
              </c-a>
            </n-li>
          </n-ul>
        </c-modal>
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
            v-if="atLeastMaintainer"
            :show-icon="false"
            @positive-click="deleteVolume(volumeId)"
            :negative-text="null"
            style="max-width: 300px"
          >
            <template #trigger>
              <n-button text type="error" style="margin-left: 16px">
                删除
              </n-button>
            </template>
            真的要删除吗？
            <br />
            {{ volumeId }}
          </n-popconfirm>
        </n-li>
      </n-ul>

      <section-header title="日文章节" />
      <upload-button
        type="jp"
        :novel-id="novelId"
        @upload-finished="getNovel()"
      />

      <translate-options
        ref="translateOptions"
        :gnid="GenericNovelId.wenku(novelId)"
        :glossary="metadata.glossary"
        style="margin-top: 16px"
      />
      <n-divider style="margin: 16px 0 0" />

      <n-list>
        <n-list-item v-for="volume of metadata.volumeJp" :key="volume.volumeId">
          <WenkuVolume
            :novel-id="novelId"
            :volume="volume"
            :get-params="() => translateOptions!!.getTranslationOptions()"
            @delete="deleteVolume(volume.volumeId)"
          />
        </n-list-item>
      </n-list>

      <CommentList :site="`wenku-${novelId}`" />
    </c-result>
  </div>
</template>
