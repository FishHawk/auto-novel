<script lang="ts" setup>
import { UploadOutlined, LinkOutlined } from '@vicons/material';
import { FormInst, FormItemRule, FormRules, useMessage } from 'naive-ui';
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { useIsWideScreen } from '@/data/util';
import { fetchMetadata, prettyCover } from '@/data/util_wenku';
import coverPlaceholder from '@/images/cover_placeholder.png';

const route = useRoute();
const router = useRouter();
const isWideScreen = useIsWideScreen(850);
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

const formRules: FormRules = {
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
      formValue.value = {
        title: result.value.title,
        titleZh: result.value.titleZh,
        cover: prettyCover(result.value.cover),
        authors: result.value.authors,
        artists: result.value.artists,
        r18: result.value.r18,
        introduction: result.value.introduction,
        volumes: result.value.volumes.map(({ asin, title, cover }) => ({
          asin,
          title,
          cover: prettyCover(cover),
        })),
      };
    } else {
      message.error('载入失败');
    }
  }
});

const submit = async () => {
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
};

const amazonUrl = ref('');
const fetchMetadataFromAmazon = async () => {
  try {
    const amazonMetadata = await fetchMetadata(amazonUrl.value);
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
      authors:
        formValue.value.authors.length > 0
          ? formValue.value.authors
          : amazonMetadata.authors,
      artists:
        formValue.value.artists.length > 0
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
};

const submitCurrentStep = ref(1);
const title = computed(() => formValue.value.title);
const similarNovels = ref<WenkuNovelOutlineDto[] | null>(null);

watch(title, () => {
  similarNovels.value = null;
  submitCurrentStep.value = 1;
});
const findSimilarNovels = async () => {
  const query = title.value.split(
    /[^\u3040-\u309f\u30a0-\u30ff\u4e00-\u9faf\u3400-\u4dbf]/,
    2
  )[0];
  const result = await ApiWenkuNovel.listNovel({
    page: 0,
    pageSize: 6,
    query,
    level: 0,
  });
  if (result.ok) {
    similarNovels.value = result.value.items;
  } else {
    message.error('搜索相似小说失败:' + result.error.message);
  }
};
const confirmNovelNotExist = () => {
  if (submitCurrentStep.value === 1) {
    submitCurrentStep.value = 2;
  }
};

const moveVolumeUp = (index: number) => {
  if (index > 0) {
    const temp = formValue.value.volumes[index];
    formValue.value.volumes[index] = formValue.value.volumes[index - 1];
    formValue.value.volumes[index - 1] = temp;
  }
};
const deleteVolume = (index: number) => {
  formValue.value.volumes = formValue.value.volumes.filter(
    (it, i) => i !== index
  );
};
</script>

<template>
  <div class="layout-content">
    <n-h1>{{ novelId === undefined ? '新建' : '编辑' }}文库小说</n-h1>

    <n-card
      v-if="novelId === undefined"
      embedded
      :bordered="false"
      style="margin-bottom: 20px"
    >
      <b style="color: red">创建文库小说之前一定要看！！</b>
      <n-ul>
        <n-li>
          文库小说只允许已经发行单行本的小说，原则上以亚马逊上可以买到为准。
        </n-li>
        <n-li> 导入时优先导入系列链接，系列小说不要导入单本。 </n-li>
        <n-li> 导入时请注意不要导入漫画。 </n-li>
        <n-li> 请确定文库小说列表里面没有这本，不要重复创建。 </n-li>
        <n-li>
          请正常填写中文标题，没有公认的标题可以尝试自行翻译，禁止复制日文标题作为中文标题。
        </n-li>
        <n-li>
          请不要创建空的文库页，尤其是不要创建文库页再去寻找资源，最后发现资源用不了。
        </n-li>
        <n-li>如果你搜不了R18，就不要创建R18页面，因为创建了也看不了。</n-li>
      </n-ul>
    </n-card>

    <n-flex style="margin-bottom: 24px" :wrap="false">
      <n-image
        width="160"
        :src="formValue.cover ? formValue.cover : coverPlaceholder"
        alt="cover"
      />

      <div style="max-width: 530px">
        <n-p>
          可以通过亚马逊系列链接/单本链接导入，你也可以输入小说标题从搜索导入。
          <br />
          导入R18书需要安装v1.0.7以上的版本的插件，并在亚马逊上点过“已满18岁”。
        </n-p>
        <n-input-group>
          <n-input
            v-model:value="amazonUrl"
            placeholder="从亚马逊导入..."
            :input-props="{ spellcheck: false }"
          />
          <n-button type="primary" @click="fetchMetadataFromAmazon()">
            导入
          </n-button>
        </n-input-group>
      </div>
    </n-flex>

    <n-form
      ref="formRef"
      :model="formValue"
      :rules="formRules"
      :label-placement="isWideScreen ? 'left' : 'top'"
      label-width="auto"
      style="max-width: 800px"
    >
      <n-form-item-row path="title" label="日文标题">
        <n-input
          v-model:value="formValue.title"
          placeholder="请输入日文标题"
          maxlength="80"
          show-count
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>

      <n-form-item-row path="titleZh" label="中文标题">
        <n-input
          v-model:value="formValue.titleZh"
          placeholder="请输入中文标题"
          maxlength="80"
          show-count
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>

      <n-form-item-row path="cover" label="封面链接">
        <n-input
          v-model:value="formValue.cover"
          placeholder="请输入封面链接"
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>

      <n-form-item-row path="authors" label="作者">
        <n-dynamic-tags v-model:value="formValue.authors" />
      </n-form-item-row>

      <n-form-item-row path="artists" label="插图">
        <n-dynamic-tags v-model:value="formValue.artists" />
      </n-form-item-row>

      <n-form-item-row path="r18" label="R18">
        <n-switch v-model:value="formValue.r18" />
      </n-form-item-row>

      <n-form-item-row path="content" label="简介">
        <n-input
          v-model:value="formValue.introduction"
          type="textarea"
          placeholder="请输入小说简介"
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

    <n-h2 prefix="bar">分卷</n-h2>
    <n-image-group show-toolbar-tooltip>
      <n-list>
        <n-list-item v-for="(volume, index) in formValue.volumes">
          <n-flex :wrap="false">
            <n-image
              width="104"
              :src="volume.cover"
              :alt="volume.asin"
              lazy
              style="border-radius: 2px"
            />

            <n-flex vertical style="flex: auto">
              <n-a :href="`https://www.amazon.co.jp/-/zh/dp/${volume.asin}`">
                ASIN: {{ volume.asin }}
              </n-a>
              <n-input
                v-model:value="volume.title"
                placeholder="日文标题"
                :input-props="{ spellcheck: false }"
              />
              <n-input
                v-model:value="volume.cover"
                placeholder="封面链接"
                :input-props="{ spellcheck: false }"
              />
              <n-flex>
                <c-button label="删除" @click="deleteVolume(index)" />
                <c-button
                  v-if="index > 0"
                  label="上移"
                  @click="moveVolumeUp(index)"
                />
              </n-flex>
            </n-flex>
          </n-flex>
        </n-list-item>
      </n-list>
    </n-image-group>

    <n-divider />

    <c-button
      v-if="novelId"
      label="提交"
      :icon="UploadOutlined"
      async
      require-login
      size="large"
      type="primary"
      class="float"
      @click="submit"
    />

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
                <router-link :to="`/wenku/${item.id}`">
                  <ImageCard
                    :src="item.cover"
                    :title="item.titleZh ? item.titleZh : item.title"
                  />
                </router-link>
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
        <c-button
          label="提交"
          :icon="UploadOutlined"
          async
          require-login
          type="primary"
          :disabled="submitCurrentStep !== 2"
          @click="submit"
        />
      </n-step>
    </n-steps>
  </div>
</template>
