<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { UploadFileInfo, useMessage } from 'naive-ui';
import {
  UploadFilled,
  EditNoteFilled,
  FavoriteBorderFilled,
  FavoriteFilled,
} from '@vicons/material';

import { ResultState } from '@/data/api/result';
import ApiUser from '@/data/api/api_user';
import { ApiWenkuNovel, WenkuMetadataDto } from '@/data/api/api_wenku_novel';
import { useAuthInfoStore, atLeastMaintainer } from '@/data/stores/authInfo';

const authInfoStore = useAuthInfoStore();

const message = useMessage();

const route = useRoute();
const novelId = route.params.novelId as string;

const novelMetadata = ref<ResultState<WenkuMetadataDto>>();

onMounted(() => getMetadata());
async function getMetadata() {
  const result = await ApiWenkuNovel.getMetadata(novelId, authInfoStore.token);
  novelMetadata.value = result;
  if (result.ok) {
    document.title = result.value.title;
  }
}

async function refreshMetadata() {
  const result = await ApiWenkuNovel.getMetadata(novelId, authInfoStore.token);
  if (result.ok) {
    novelMetadata.value = result;
  }
}

async function beforeUpload({ file }: { file: UploadFileInfo }) {
  if (!authInfoStore.token) {
    message.info('请先登录');
    return false;
  }
  if (file.file?.size && file.file.size > 1024 * 1024 * 40) {
    message.error('文件大小不能超过20MB');
    return false;
  }
  if (file.type === 'application/epub+zip' || file.type === 'text/plain') {
    return true;
  } else {
    message.error('只能上传epub或txt格式的文件');
    return false;
  }
}
function handleFinish({
  file,
  event,
}: {
  file: UploadFileInfo;
  event?: ProgressEvent;
}) {
  refreshMetadata();
  return undefined;
}

var isFavoriteChanging = false;

async function addFavorite() {
  if (isFavoriteChanging) return;
  isFavoriteChanging = true;

  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }

  const result = await ApiUser.putFavoritedWenkuNovel(novelId, token);
  if (result.ok) {
    if (novelMetadata.value?.ok) {
      novelMetadata.value.value.favored = true;
    }
  } else {
    message.error('收藏错误：' + result.error.message);
  }
  isFavoriteChanging = false;
}

async function removeFavorite() {
  if (isFavoriteChanging) return;
  isFavoriteChanging = true;

  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }

  const result = await ApiUser.deleteFavoritedWenkuNovel(novelId, token);
  if (result.ok) {
    if (novelMetadata.value?.ok) {
      novelMetadata.value.value.favored = false;
    }
  } else {
    message.error('取消收藏错误：' + result.error.message);
  }
  isFavoriteChanging = false;
}

const editMode = ref(false);
function enableEditMode() {
  if (!atLeastMaintainer(authInfoStore.role)) {
    message.info('权限不够');
    return;
  }
  editMode.value = true;
}
</script>

<template>
  <MainLayout>
    <template v-slot:full-width>
      <div
        v-if="novelMetadata?.ok"
        :style="{
          background:
            'linear-gradient( to top, rgba(255, 255, 255, 1), rgba(255, 255, 255, 0.4)), ' +
            `url(${novelMetadata.value.cover})`,
        }"
        style="
          width: 100%;
          clip: rect(0, auto, auto, 0);
          background-size: cover;
          background-position: center 15%;
        "
      >
        <div style="width: 100%; height: 100%; backdrop-filter: blur(4px)">
          <div class="container" style="filter: ">
            <n-space
              :wrap="false"
              style="padding-top: 40px; padding-bottom: 20px; min-height: 260px"
            >
              <n-card size="small" style="width: 160px">
                <template #cover>
                  <img :src="novelMetadata.value.cover" alt="cover" />
                </template>
              </n-card>
              <div>
                <n-h1 prefix="bar" style="font-size: 22px; font-weight: 900">
                  {{
                    novelMetadata.value.titleZh
                      ? novelMetadata.value.titleZh
                      : novelMetadata.value.title
                  }}
                </n-h1>

                <table style="border-spacing: 0px 8px">
                  <TagGroup
                    v-if="novelMetadata.value.authors.length > 0"
                    label="作者"
                    :tags="novelMetadata.value.authors"
                  />
                  <TagGroup
                    v-if="novelMetadata.value.artists.length > 0"
                    label="插图"
                    :tags="novelMetadata.value.artists"
                  />
                  <TagGroup
                    v-if="novelMetadata.value.keywords.length > 0"
                    class="on-desktop"
                    label="标签"
                    :tags="novelMetadata.value.keywords"
                  />
                </table>
              </div>
            </n-space>
          </div>
        </div>
      </div>
    </template>

    <div v-if="novelMetadata?.ok">
      <n-space>
        <templage v-if="atLeastMaintainer(authInfoStore.role)">
          <n-button v-if="!editMode" @click="enableEditMode()">
            <template #icon>
              <n-icon> <EditNoteFilled /> </n-icon>
            </template>
            编辑
          </n-button>

          <n-button v-else @click="editMode = false">
            <template #icon>
              <n-icon> <EditNoteFilled /> </n-icon>
            </template>
            退出编辑
          </n-button>
        </templage>

        <n-button
          v-if="novelMetadata.value.favored === true"
          @click="removeFavorite()"
        >
          <template #icon>
            <n-icon> <FavoriteFilled /> </n-icon>
          </template>
          取消收藏
        </n-button>

        <n-button v-else @click="addFavorite()">
          <template #icon>
            <n-icon> <FavoriteBorderFilled /> </n-icon>
          </template>
          收藏
        </n-button>
      </n-space>

      <template v-if="editMode">
        <WenkuEditSection
          :id="novelId"
          v-model:metadata="novelMetadata.value"
        />
      </template>

      <template v-else>
        <n-p>原名：{{ novelMetadata.value.title }}</n-p>
        <n-p
          v-html="novelMetadata.value.introduction.replace(/\n/g, '<br />')"
        />

        <div class="on-mobile">
          <n-space :size="[4, 4]">
            <n-tag
              v-for="tag of novelMetadata.value.keywords"
              :bordered="false"
              size="small"
            >
              {{ tag }}
            </n-tag>
          </n-space>
        </div>

        <n-upload
          multiple
          :headers="{ Authorization: 'Bearer ' + authInfoStore.token }"
          :action="ApiWenkuNovel.createVolumeZhUploadUrl(novelId)"
          :trigger-style="{ width: '100%' }"
          @finish="handleFinish"
          @before-upload="beforeUpload"
        >
          <n-space align="baseline" justify="space-between" style="width: 100">
            <n-h2 prefix="bar">目录</n-h2>
            <n-button v-if="atLeastMaintainer(authInfoStore.role)">
              <template #icon><n-icon :component="UploadFilled" /></template>
              上传章节
            </n-button>
          </n-space>
        </n-upload>

        <n-ul>
          <n-li
            v-for="fileName in novelMetadata.value.volumeZh.sort((a, b) =>
              a.localeCompare(b)
            )"
          >
            <n-a
              :href="`/files-wenku/${novelId}/${fileName}`"
              target="_blank"
              :download="fileName"
            >
              {{ fileName }}
            </n-a>
          </n-li>
        </n-ul>

        <n-empty
          v-if="novelMetadata.value.volumeZh.length === 0"
          description="空列表"
        />

        <CommentList :post-id="route.path" />
      </template>
    </div>

    <div v-if="novelMetadata && !novelMetadata.ok">
      <n-result
        status="error"
        title="加载错误"
        :description="novelMetadata.error.message"
      />
    </div>
  </MainLayout>
</template>
