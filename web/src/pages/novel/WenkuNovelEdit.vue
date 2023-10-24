<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules, useMessage } from 'naive-ui';
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { fetchMetadata } from '@/data/util_wenku';
import coverPlaceholder from '@/images/cover_placeholder.png';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const novelId = route.params.id as string | undefined;

const formRef = ref<FormInst | null>(null);

const formValue = ref({
  title: '',
  titleZh: '',
  cover: '',
  authors: [] as string[],
  artists: [] as string[],
  r18: false,
  introduction: '',
  volumes: [] as { asin: string; title: string; cover: string }[],
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
      formValue.value.authors = result.value.authors;
      formValue.value.artists = result.value.artists;
      formValue.value.r18 = result.value.r18;
      formValue.value.introduction = result.value.introduction;
      formValue.value.volumes = result.value.volumes;
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
    authors: formValue.value.authors,
    artists: formValue.value.artists,
    r18: formValue.value.r18,
    introduction: formValue.value.introduction,
    volumes: formValue.value.volumes,
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

const url = ref('');
async function fetchNovel() {
  try {
    const amazonMetadata = await fetchMetadata(url.value);
    const volumesOld = formValue.value.volumes.map((oldV) => {
      const newV = amazonMetadata.volumes.find((it) => it.asin === oldV.asin);
      if (newV === undefined) {
        return oldV;
      } else {
        return newV;
      }
    });
    const volumesNew = amazonMetadata.volumes.filter(
      (newV) => !formValue.value.volumes.some((oldV) => oldV.asin === newV.asin)
    );

    const volumes = volumesOld.concat(volumesNew);
    formValue.value = {
      title: formValue.value.title
        ? formValue.value.title
        : amazonMetadata.title,
      titleZh: formValue.value.titleZh,
      cover: amazonMetadata.cover,
      authors: formValue.value.authors
        ? formValue.value.authors
        : amazonMetadata.authors,
      artists: formValue.value.artists
        ? formValue.value.artists
        : amazonMetadata.artists,
      r18: amazonMetadata.r18,
      introduction: formValue.value.introduction
        ? formValue.value.introduction
        : amazonMetadata.introduction,
      volumes,
    };
  } catch (e) {
    message.error(`获取失败:${e}`);
  }
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

function moveVolumeUp(index: number) {
  if (index > 0) {
    const temp = formValue.value.volumes[index];
    formValue.value.volumes[index] = formValue.value.volumes[index - 1];
    formValue.value.volumes[index - 1] = temp;
  }
}

function deleteVolume(index: number) {
  formValue.value.volumes = formValue.value.volumes.filter(
    (it, i) => i !== index
  );
}
</script>

<template>
  <MainLayout>
    <n-h1>{{ novelId === undefined ? '新建' : '编辑' }}文库小说</n-h1>

    <n-space style="margin-bottom: 24px" :wrap="false">
      <div>
        <img
          :src="formValue.cover ? formValue.cover : coverPlaceholder"
          alt="cover"
          style="width: 160px"
        />
        <br />
        <n-text depth="3" style="font-size: 12px">
          * 暂不支持上传封面。
        </n-text>
      </div>

      <n-p style="max-width: 530px">
        可以通过亚马逊系列链接/单本链接导入，你也可以输入小说标题从搜索导入。
        <br />
        亚马逊有些R18书导入会出错。这种时候按下面步骤操作：
        <n-ol>
          <n-li>确保安装的插件是最新的v1.0.7。</n-li>
          <n-li>确保你之前点过“已满18岁”。</n-li>
          <n-li>确保现在没有打开亚马逊页面。</n-li>
          <n-li>右键插件，选择“强制公开亚马逊Cookies”。</n-li>
        </n-ol>

        <n-input-group>
          <n-input
            v-model:value="url"
            placeholder="从亚马逊导入..."
            :input-props="{ spellcheck: false }"
          />
          <n-button type="primary" @click="fetchNovel()"> 导入 </n-button>
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

      <n-form-item-row path="r18">
        <n-text style="white-space: nowrap">R18：</n-text>
        <n-switch v-model:value="formValue.r18" />
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

    <n-list>
      <n-list-item v-for="(volume, index) in formValue.volumes">
        <n-space :wrap="false">
          <n-card size="small" style="width: 100px">
            <template #cover>
              <img :src="volume.cover" alt="cover" />
            </template>
          </n-card>
          <n-space vertical>
            <n-p>ASIN: {{ volume.asin }}</n-p>
            <n-p>日文标题: {{ volume.title }}</n-p>
            <n-input
              v-model:value="volume.title"
              placeholder="日文标题"
              show-count
              :input-props="{ spellcheck: false }"
            />
            <n-space>
              <n-button @click="deleteVolume(index)"> 删除 </n-button>
              <n-button v-if="index > 0" @click="moveVolumeUp(index)">
                上移
              </n-button>
            </n-space>
          </n-space>
        </n-space>
      </n-list-item>
    </n-list>

    <n-divider />

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
