<script lang="ts" setup>
import { FormRules, FormItemRule, FormInst, useMessage } from 'naive-ui';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import coverPlaceholder from '@/images/cover_placeholder.png';
import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { useUserDataStore } from '@/data/stores/userData';
import { watch } from 'vue';

const route = useRoute();
const router = useRouter();
const message = useMessage();
const userData = useUserDataStore();

const novelId = route.params.id as string | undefined;

const formRef = ref<FormInst | null>(null);

const formValue = ref({
  title: '',
  titleZh: '',
  cover: '',
  coverSmall: '',
  authors: [] as string[],
  artists: [] as string[],
  keywords: [] as string[],
  introduction: '',
});

const rules: FormRules = {
  title: [
    {
      validator: (_rule: FormItemRule, value: string) =>
        value.trim().length > 0,
      message: '标题不能为空',
      trigger: 'input',
    },
    {
      validator: (_rule: FormItemRule, value: string) => value.length <= 80,
      message: '标题长度不能超过80个字符',
      trigger: 'input',
    },
  ],
  titleZh: [
    {
      validator: (_rule: FormItemRule, value: string) =>
        value.trim().length > 0,
      message: '标题不能为空',
      trigger: 'input',
    },
    {
      validator: (_rule: FormItemRule, value: string) => value.length <= 80,
      message: '标题长度不能超过80个字符',
      trigger: 'input',
    },
  ],
  introduction: [
    {
      validator: (_rule: FormItemRule, value: string) => value.length <= 500,
      message: '简介长度不能超过500个字符',
      trigger: 'input',
    },
  ],
};

onMounted(async () => {
  if (novelId !== undefined) {
    const result = await ApiWenkuNovel.getNovel(novelId);
    if (result.ok) {
      formValue.value.title = result.value.title;
      formValue.value.titleZh = result.value.titleZh;
      formValue.value.cover = result.value.cover;
      formValue.value.coverSmall = result.value.coverSmall;
      formValue.value.authors = result.value.authors;
      formValue.value.artists = result.value.artists;
      formValue.value.keywords = result.value.keywords;
      formValue.value.introduction = result.value.introduction;
    } else {
      message.error('载入失败');
    }
  }
});

async function submit() {
  const validated = await new Promise<boolean>(function (resolve, _reject) {
    formRef.value?.validate((errors) => {
      if (errors) resolve(false);
      else resolve(true);
    });
  });
  if (!validated) return;

  const body = {
    title: formValue.value.title,
    titleZh: formValue.value.titleZh,
    cover: formValue.value.cover,
    coverSmall: formValue.value.coverSmall,
    authors: formValue.value.authors,
    artists: formValue.value.artists,
    keywords: formValue.value.keywords,
    introduction: formValue.value.introduction,
  };

  if (novelId === undefined) {
    const result = await ApiWenkuNovel.createNovel(body);
    if (result.ok) {
      message.info('新建文库成功');
      router.push({ path: `/wenku/${result.value}` });
    } else {
      message.error('新建文库失败:' + result.error.message);
    }
  } else {
    const result = await ApiWenkuNovel.updateNovel(novelId, body);
    if (result.ok) {
      message.info('编辑文库成功');
      router.push({ path: `/wenku/${novelId}` });
    } else {
      message.error('编辑文库失败:' + result.error.message);
    }
  }
}

const importUrl = ref('');
async function importNovel() {
  const url = importUrl.value;

  const importFromBungumi = () => {
    const bid = /bangumi\.tv\/subject\/([0-9]+)/.exec(url)?.[1];
    if (!bid) return null;
    return ApiWenkuNovel.getNovelFromBangumi(bid);
  };

  const importers: [string, typeof importFromBungumi][] = [
    ['Bangumi', importFromBungumi],
  ];

  for (const [label, importer] of importers) {
    const promise = importer();
    if (promise) {
      const result = await promise;
      if (result.ok) {
        formValue.value.title = result.value.title;
        formValue.value.titleZh = result.value.titleZh;
        formValue.value.cover = result.value.cover;
        formValue.value.coverSmall = result.value.coverSmall;
        formValue.value.authors = result.value.authors;
        formValue.value.artists = result.value.artists;
        formValue.value.keywords = result.value.keywords;
        formValue.value.introduction = result.value.introduction;
      } else {
        message.error(label + '导入失败:' + result.error.message);
      }
      return;
    }
  }

  message.error('无法解析链接');
}

const submitCurrentStep = ref(1);
const title = computed(() => formValue.value.title);
const similarNovels = ref<WenkuNovelOutlineDto[] | null>(null);

watch(title, () => {
  similarNovels.value = null;
  submitCurrentStep.value = 1;
});
async function findSimilarNovels() {
  const result = await ApiWenkuNovel.list({
    page: 0,
    pageSize: 6,
    query: title.value,
  });
  if (result.ok) {
    similarNovels.value = result.value.items;
  } else {
    message.error('搜索相似小说失败:' + result.error.message);
  }
}
function confirmNovelNotExist() {
  if (submitCurrentStep.value === 1) {
    submitCurrentStep.value = 2;
  }
}
</script>

<template>
  <MainLayout>
    <n-h1>{{ novelId === undefined ? '新建' : '编辑' }}文库小说</n-h1>

    <n-space style="margin-bottom: 24px">
      <div>
        <img
          :src="formValue.cover ? formValue.cover : coverPlaceholder"
          alt="cover"
          style="width: 160px"
        />
        <br />
        <n-text depth="3" style="font-size: 12px">
          * 暂不支持上传封面。
          <!-- 如果没设置封面，会自动使用上传的第一本Epub的封面。 -->
        </n-text>
      </div>

      <n-p>
        可以从其他网站导入数据，目前支持：
        <n-ul>
          <n-li><b>Bangumi</b>: https://bangumi.tv/subject/101114</n-li>
        </n-ul>
        <n-input-group>
          <n-input
            v-model:value="importUrl"
            placeholder="从链接导入..."
            :input-props="{ spellcheck: false }"
          />
          <n-button type="primary" @click="importNovel()"> 导入 </n-button>
        </n-input-group>
      </n-p>
    </n-space>

    <n-form
      ref="formRef"
      :model="formValue"
      :rules="rules"
      label-placement="left"
      style="max-width: 800px"
    >
      <n-form-item-row v-if="userData.asAdmin">
        <n-input
          v-model:value="formValue.cover"
          placeholder="封面"
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>

      <n-form-item-row v-if="userData.asAdmin">
        <n-input
          v-model:value="formValue.coverSmall"
          placeholder="封面/小"
          show-count
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>

      <n-form-item-row path="title">
        <n-input
          v-model:value="formValue.title"
          placeholder="日文标题"
          maxlength="80"
          show-count
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>

      <n-form-item-row path="titleZh">
        <n-input
          v-model:value="formValue.titleZh"
          placeholder="中文标题"
          maxlength="80"
          show-count
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>

      <n-form-item-row path="authors">
        <n-text style="white-space: nowrap">作者：</n-text>
        <n-dynamic-tags v-model:value="formValue.authors" />
      </n-form-item-row>

      <n-form-item-row path="artists">
        <n-text style="white-space: nowrap">插图：</n-text>
        <n-dynamic-tags v-model:value="formValue.artists" />
      </n-form-item-row>

      <n-form-item-row v-if="userData.asAdmin" path="keywords">
        <n-text style="white-space: nowrap">标签：</n-text>
        <n-dynamic-tags v-model:value="formValue.keywords" />
      </n-form-item-row>

      <n-form-item-row path="content">
        <n-input
          v-model:value="formValue.introduction"
          type="textarea"
          placeholder="描述"
          :autosize="{
            minRows: 8,
            maxRows: 24,
          }"
          maxlength="500"
          show-count
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>
    </n-form>

    <AsyncButton v-if="novelId" type="primary" :on-async-click="submit">
      提交
    </AsyncButton>

    <n-steps
      v-else
      :current="submitCurrentStep"
      vertical
      style="margin-left: 8px"
    >
      <n-step title="检查小说是否已经存在">
        <div class="n-step-description">
          <n-p>
            请点击搜索按钮，确定你要创建的小说确实还不存在，再进行下一步。
          </n-p>
          <n-p>
            <span v-if="similarNovels === null"> 未搜索 </span>
            <span v-else-if="similarNovels.length === 0"> 没有相似的小说 </span>
            <n-grid v-else :x-gap="12" :y-gap="12" cols="3 600:6">
              <n-grid-item v-for="item in similarNovels">
                <RouterNA :to="`/wenku/${item.id}`">
                  <ImageCard
                    :src="item.cover"
                    :title="item.titleZh ? item.titleZh : item.title"
                  />
                </RouterNA>
              </n-grid-item>
            </n-grid>
          </n-p>

          <n-p>
            <n-button-group>
              <n-button @click="findSimilarNovels()"> 搜索相似小说 </n-button>
              <n-button @click="confirmNovelNotExist()">
                确定小说不存在
              </n-button>
            </n-button-group>
          </n-p>
        </div>
      </n-step>
      <n-step title="创建小说">
        <div class="n-step-description"></div>
        <n-button
          :disabled="submitCurrentStep !== 2"
          type="primary"
          @click="submit()"
        >
          提交
        </n-button>
      </n-step>
    </n-steps>
  </MainLayout>
</template>
