<script lang="ts" setup>
import { EditNoteFilled } from '@vicons/material';
import { useMessage, useThemeVars } from 'naive-ui';
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';

import {
  ApiWenkuNovel,
  VolumeJpDto,
  WenkuNovelDto,
} from '@/data/api/api_wenku_novel';
import { ResultState } from '@/data/result';
import { useSettingStore } from '@/data/stores/setting';
import coverPlaceholder from '@/images/cover_placeholder.png';

const setting = useSettingStore();
const message = useMessage();

const route = useRoute();
const novelId = route.params.novelId as string;

const novelMetadataResult = ref<ResultState<WenkuNovelDto>>();

async function getMetadata() {
  const result = await ApiWenkuNovel.getNovel(novelId);
  novelMetadataResult.value = result;
  if (result.ok) {
    document.title = result.value.title;
  }
}
getMetadata();

async function refreshMetadata() {
  const result = await ApiWenkuNovel.getNovel(novelId);
  if (result.ok) {
    novelMetadataResult.value = result;
  }
}

const editMode = ref(false);

function sortVolumesZh(volumes: string[]) {
  return volumes.sort((a, b) => a.localeCompare(b));
}
const vars = useThemeVars();

//
const gptAccessToken = ref('');
const gptAccessTokenOptions = computed(() => {
  return setting.openAiAccessTokens.map((t) => {
    return { label: t, value: t };
  });
});

const showDownloadOptions = ref(false);
const showTranslateOptions = ref(false);
const translateExpireChapter = ref(false);

function toggleTranslateOptions() {
  if (showTranslateOptions.value) {
    showTranslateOptions.value = false;
  } else {
    showTranslateOptions.value = true;
    showDownloadOptions.value = false;
  }
}

function toggleDownloadOptions() {
  if (showDownloadOptions.value) {
    showDownloadOptions.value = false;
  } else {
    showDownloadOptions.value = true;
    showTranslateOptions.value = false;
  }
}

async function submitGlossary(glossary: { [key: string]: string }) {
  const result = await ApiWenkuNovel.updateGlossary(novelId, glossary);
  if (result.ok) {
    message.success('术语表提交成功');
  } else {
    message.error('术语表提交失败：' + result.error.message);
  }
}

const modeOptions = [
  { value: 'zh', label: '中文' },
  { value: 'mix', label: '中文/日文' },
  { value: 'mix-reverse', label: '日文/中文' },
];
const translationModeOptions = [
  { label: '优先', value: 'priority' },
  { label: '并列', value: 'parallel' },
];
const translationOptions = [
  { label: 'Sakura', value: 'sakura' },
  { label: 'GPT3', value: 'gpt' },
  { label: '有道', value: 'youdao' },
  { label: '百度', value: 'baidu' },
];

function sortVolumesJp(volumes: VolumeJpDto[]) {
  return volumes.sort((a, b) => a.volumeId.localeCompare(b.volumeId));
}
</script>

<template>
  <MainLayout>
    <template v-slot:full-width>
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
          <n-layout class="container" style="background-color: transparent">
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
                  <TagGroup
                    v-if="novelMetadataResult.value.authors.length > 0"
                    label="作者"
                    :tags="novelMetadataResult.value.authors"
                  />
                  <TagGroup
                    v-if="novelMetadataResult.value.artists.length > 0"
                    label="插图"
                    :tags="novelMetadataResult.value.artists"
                  />
                </table>
              </div>
            </n-space>
          </n-layout>
        </div>
      </div>
    </template>

    <ResultView
      :result="novelMetadataResult"
      :showEmpty="() => false"
      v-slot="{ value: metadata }"
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
          :favored="metadata.favored"
          :favored-list="metadata.favoredList"
          :novel="{ type: 'wenku', novelId }"
          @update:favored="getMetadata"
        />

        <n-a
          :href="`https://www.amazon.co.jp/s?k=${metadata.title}&rh=n%3A465392`"
          target="_blank"
        >
          <n-button>在亚马逊搜索</n-button>
        </n-a>
      </n-space>

      <template v-if="editMode">
        <WenkuEdit :id="novelId" :metadata="metadata" />
      </template>

      <template v-else>
        <n-p>原名：{{ metadata.title }}</n-p>
        <n-p v-html="metadata.introduction.replace(/\n/g, '<br />')" />

        <n-space :size="[4, 4]">
          <n-tag
            v-for="tag of metadata.keywords"
            :bordered="false"
            size="small"
          >
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
        <UploadButton
          type="zh"
          :novelId="novelId"
          @uploadFinished="refreshMetadata()"
        />
        <n-ul>
          <n-li v-for="fileName in sortVolumesZh(metadata.volumeZh)">
            <n-a
              :href="`/files-wenku/${novelId}/${fileName}`"
              target="_blank"
              :download="fileName"
            >
              {{ fileName }}
            </n-a>
          </n-li>
        </n-ul>

        <SectionHeader title="日文章节" />
        <UploadButton
          type="jp"
          :novelId="novelId"
          @uploadFinished="refreshMetadata()"
        />

        <n-p depth="3" style="font-size: 12px">
          # 翻译功能需要需要安装浏览器插件，参见
          <RouterNA to="/forum/64f3d63f794cbb1321145c07">插件使用说明</RouterNA>
        </n-p>
        <n-button-group style="margin-bottom: 8px">
          <n-button v-if="metadata.glossary" @click="toggleTranslateOptions()">
            翻译设置
          </n-button>
          <n-button @click="toggleDownloadOptions()">下载设置</n-button>
        </n-button-group>

        <n-collapse-transition
          :show="showTranslateOptions || showDownloadOptions"
          style="margin-bottom: 16px"
        >
          <n-list v-if="showTranslateOptions && metadata.glossary" bordered>
            <n-list-item>
              <AdvanceOptionSwitch
                title="翻译过期章节"
                description="在启动翻译任务时，重新翻译术语表过期的章节。一次性设定，默认关闭。"
                v-model:value="translateExpireChapter"
              />
            </n-list-item>
            <n-list-item>
              <AdvanceOption
                title="术语表"
                description="术语表过大可能会使得翻译质量下降（例如：百度/有道将无法从判断人名性别，导致人称代词错误）。"
              >
                <GlossaryEdit
                  :glossary="metadata.glossary"
                  :submit="() => submitGlossary(metadata.glossary)"
                />
              </AdvanceOption>
            </n-list-item>
          </n-list>

          <n-list v-if="showDownloadOptions" bordered>
            <n-list-item>
              <AdvanceOptionSwitch
                title="下载文件格式与阅读设置一致"
                description="使用在线章节的阅读设置作为下载文件的格式，启用时会禁止下面的自定义设置。"
                v-model:value="setting.isDownloadFormatSameAsReaderFormat"
              />
            </n-list-item>

            <n-list-item>
              <AdvanceOptionRadio
                title="自定义下载文件语言"
                description="设置下载文件的语言。注意部分Epub阅读器不支持自定义字体颜色，日文段落会被强制使用黑色字体。"
                v-model:value="setting.downloadFormat.mode"
                :disabled="setting.isDownloadFormatSameAsReaderFormat"
                :options="modeOptions"
              />
            </n-list-item>

            <n-list-item>
              <AdvanceOptionRadio
                title="自定义下载文件翻译"
                description="设置下载文件使用的翻译。注意右侧选中的翻译的顺序，优先模式顺序代表优先级，并列模式顺序代表翻译的排列顺序。"
                v-model:value="setting.downloadFormat.translationsMode"
                :disabled="setting.isDownloadFormatSameAsReaderFormat"
                :options="translationModeOptions"
              >
                <n-transfer
                  v-model:value="setting.downloadFormat.translations"
                  :options="translationOptions"
                  :disabled="setting.isDownloadFormatSameAsReaderFormat"
                  size="small"
                  style="height: 190px; margin-top: 8px; font-size: 12px"
                />
              </AdvanceOptionRadio>
            </n-list-item>
          </n-list>
        </n-collapse-transition>

        <n-auto-complete
          v-model:value="gptAccessToken"
          :options="gptAccessTokenOptions"
          placeholder="请输入ChatGPT的Access Token或者Api Key"
          :get-show="() => true"
        />

        <n-list>
          <template v-for="volume of sortVolumesJp(metadata.volumeJp)">
            <n-list-item>
              <WenkuVolume
                :novel-id="novelId"
                :volume="volume"
                :get-params="
                  () => ({
                    accessToken: gptAccessToken,
                    translateExpireChapter,
                  })
                "
                @deleted="refreshMetadata()"
              />
            </n-list-item>
          </template>
        </n-list>

        <CommentList :site="`wenku-${novelId}`" />
      </template>
    </ResultView>
  </MainLayout>
</template>
