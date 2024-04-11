<script lang="ts" setup>
import { UploadOutlined } from '@vicons/material';
import { FormInst, FormItemRule, FormRules } from 'naive-ui';

import { Locator } from '@/data';
import coverPlaceholder from '@/image/cover_placeholder.png';
import {
  presetKeywordsNonR18,
  presetKeywordsR18,
  WenkuNovelOutlineDto,
  WenkuVolumeDto,
} from '@/model/WenkuNovel';
import { delay, parallelExec } from '@/util';
import { runCatching } from '@/util/result';

import { doAction, useIsWideScreen } from '@/pages/util';

const props = defineProps<{
  novelId?: string;
}>();

const router = useRouter();
const isWideScreen = useIsWideScreen(850);
const message = useMessage();

const { atLeastMaintainer } = Locator.userDataRepository();
const { prettyCover } = Locator.amazonNovelRepository;

interface FormValue {
  title: string;
  titleZh: string;
  cover?: string;
  authors: string[];
  artists: string[];
  r18: boolean;
  keywords: string[];
  introduction: string;
  volumes: WenkuVolumeDto[];
}
const defaultFormValue: FormValue = {
  title: '',
  titleZh: '',
  cover: '',
  authors: [],
  artists: [],
  r18: false,
  keywords: [],
  introduction: '',
  volumes: [],
};

const allowSubmit = ref(false);
const formRef = ref<FormInst>();
const formValue = ref(defaultFormValue);
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

const amazonUrl = ref('');

watch(props, async ({ novelId }) => {
  formValue.value = defaultFormValue;

  if (novelId !== undefined) {
    allowSubmit.value = false;
    const result = await runCatching(
      Locator.wenkuNovelRepository.getNovel(novelId)
    );

    if (props.novelId !== novelId) return;

    if (result.ok) {
      const {
        title,
        titleZh,
        cover,
        authors,
        artists,
        r18,
        keywords,
        introduction,
      } = result.value;
      formValue.value = {
        title,
        titleZh,
        cover: prettyCover(cover),
        authors,
        artists,
        r18,
        keywords,
        introduction,
        volumes: result.value.volumes.map((it) => {
          it.cover = prettyCover(it.cover);
          return it;
        }),
      };
      allowSubmit.value = true;
      if (amazonUrl.value.length === 0) {
        amazonUrl.value = result.value.title.replace(/[?？。!！]$/, '');
      }
    } else {
      message.error('载入失败');
    }
  } else {
    allowSubmit.value = true;
  }
});

const submit = async () => {
  if (!allowSubmit.value) {
    message.warning('文章未载入，无法提交');
    return;
  }

  try {
    await formRef.value?.validate();
  } catch (e) {
    return;
  }

  const allPresetKeywords = presetKeywords.value.groups.flatMap(
    (it) => it.presetKeywords
  );

  const body = {
    title: formValue.value.title,
    titleZh: formValue.value.titleZh,
    cover: formValue.value.cover,
    authors: formValue.value.authors,
    artists: formValue.value.artists,
    r18: formValue.value.r18,
    introduction: formValue.value.introduction,
    keywords: formValue.value.keywords.filter((it) =>
      allPresetKeywords.includes(it)
    ),
    volumes: formValue.value.volumes,
  };

  const { novelId } = props;
  if (novelId === undefined) {
    await doAction(
      Locator.wenkuNovelRepository
        .createNovel(body)
        .then((id) => router.push({ path: `/wenku/${id}` })),
      '新建文库',
      message
    );
  } else {
    await doAction(
      Locator.wenkuNovelRepository
        .updateNovel(novelId, body)
        .then(() => router.push({ path: `/wenku/${novelId}` })),
      '编辑文库',
      message
    );
  }
};

const populateNovelFromAmazon = async (
  urlOrQuery: string,
  forcePopulateVolumes: boolean
) => {
  const messageReactive = message.create('', {
    type: 'loading',
    duration: 0,
  });

  // 导入小说
  urlOrQuery = urlOrQuery.trim();
  if (urlOrQuery.length !== 0) {
    messageReactive.content = '导入小说';

    let amazonMetadata;
    try {
      amazonMetadata = await Locator.amazonNovelRepository.getNovel(urlOrQuery);
    } catch (e) {
      message.error(`导入小说失败:${e}`);
      return;
    }
    const volumesOld = formValue.value.volumes;
    const volumesNew = amazonMetadata.volumes.filter(
      (newV) => !formValue.value.volumes.some((oldV) => oldV.asin === newV.asin)
    );

    const volumes = volumesOld.concat(volumesNew);
    formValue.value = {
      title: formValue.value.title
        ? formValue.value.title
        : amazonMetadata.title,
      titleZh: formValue.value.titleZh,
      cover: amazonMetadata.volumes.at(0)?.cover,
      authors:
        formValue.value.authors.length > 0
          ? formValue.value.authors
          : amazonMetadata.authors,
      artists:
        formValue.value.artists.length > 0
          ? formValue.value.artists
          : amazonMetadata.artists,
      r18: amazonMetadata.r18,
      keywords: formValue.value.keywords,
      introduction: formValue.value.introduction
        ? formValue.value.introduction
        : amazonMetadata.introduction,
      volumes,
    };
  }

  // 导入分卷
  {
    const volumes = formValue.value.volumes.filter(
      ({ coverHires, publishAt }) =>
        [coverHires, publishAt].some((it) => it === undefined) ||
        forcePopulateVolumes
    );

    await parallelExec(
      volumes.map(({ asin }) => {
        return () =>
          Locator.amazonNovelRepository
            .getVolume(asin)
            .then((newVolume) => {
              const index = formValue.value.volumes.findIndex(
                (it) => it.asin === asin
              );
              if (index >= 0) {
                formValue.value.volumes[index] = newVolume;
              }
            })
            .catch((e) => {
              message.error(`导入分卷失败 ${asin} ${e}`);
            });
      }),
      5,
      (context) => {
        const processing = context.promises.length;
        const finished = context.finished;
        const size = volumes.length;
        messageReactive.content = `导入分卷[${finished}/${size}] ${processing}本处理中`;
      }
    );

    formValue.value.cover = formValue.value.volumes.at(0)?.cover;
  }

  // 结束
  messageReactive.content = '导入完成';
  messageReactive.type = 'info';
  delay(3000).then(() => messageReactive.destroy());
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
  const result = await runCatching(
    Locator.wenkuNovelRepository.listNovel({
      page: 0,
      pageSize: 6,
      query,
      level: 0,
    })
  );
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
const moveVolumeDown = (index: number) => {
  if (index < formValue.value.volumes.length - 1) {
    const temp = formValue.value.volumes[index];
    formValue.value.volumes[index] = formValue.value.volumes[index + 1];
    formValue.value.volumes[index + 1] = temp;
  }
};
const topVolume = (asin: string) => {
  formValue.value.volumes.sort((a, b) => {
    return a.asin == asin ? -1 : b.asin == asin ? 1 : 0;
  });
};
const bottomVolume = (asin: string) => {
  formValue.value.volumes.sort((a, b) => {
    return a.asin == asin ? 1 : b.asin == asin ? -1 : 0;
  });
};
const deleteVolume = (asin: string) => {
  formValue.value.volumes = formValue.value.volumes.filter(
    (it) => it.asin !== asin
  );
};

const markAsDuplicate = () => {
  formValue.value = {
    title: '重复，待删除',
    titleZh: '重复，待删除',
    cover: undefined,
    authors: [],
    artists: [],
    r18: formValue.value.r18,
    keywords: [],
    introduction: '',
    volumes: [],
  };
};

const presetKeywords = computed(() =>
  formValue.value.r18 ? presetKeywordsR18 : presetKeywordsNonR18
);
const showKeywordsModal = ref(false);

const togglePresetKeyword = (checked: boolean, keyword: string) => {
  if (checked) {
    formValue.value.keywords.push(keyword);
  } else {
    formValue.value.keywords = formValue.value.keywords.filter(
      (it) => it !== keyword
    );
  }
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
      <n-text type="error">
        <b>创建文库小说注意事项：</b>
      </n-text>
      <n-ul>
        <n-li>
          文库小说只允许已经发行单行本的小说，原则上以亚马逊上可以买到为准。
        </n-li>
        <n-li> 不要导入漫画。系列小说不要导入单本。 </n-li>
        <n-li> 不要重复创建，请确定文库小说列表里面没有这本。 </n-li>
        <n-li>
          请正常填写中文标题，没有公认的标题可以尝试自行翻译，不要复制日文标题作为中文标题。
        </n-li>
        <n-li>
          不要创建空的文库页，尤其是不要创建文库页再去寻找资源，最后发现资源用不了。
        </n-li>
        <n-li>如果你搜不了R18，就不要创建R18页面，因为创建了也看不了。</n-li>
      </n-ul>
    </n-card>

    <n-flex style="margin-bottom: 24px">
      <div>
        <n-image
          width="160"
          :src="formValue.cover ? formValue.cover : coverPlaceholder"
          alt="cover"
        />
      </div>

      <n-flex size="large" vertical style="max-width: 530px">
        <n-text>
          可以通过亚马逊系列链接/单本链接导入，你也可以输入小说标题从搜索导入。
          <br />
          导入R18书需要安装v1.0.7以上的版本的插件，并在亚马逊上点过“已满18岁”。
        </n-text>
        <n-input-group>
          <n-input
            v-model:value="amazonUrl"
            :placeholder="formValue.title"
            :input-props="{ spellcheck: false }"
          />
          <c-button
            label="导入"
            :round="false"
            type="primary"
            @action="populateNovelFromAmazon(amazonUrl, false)"
          />
        </n-input-group>
        <n-flex>
          <c-button
            label="在亚马逊搜索"
            secondary
            tag="a"
            :href="`https://www.amazon.co.jp/s?k=${encodeURIComponent(
              formValue.title
            )}&i=stripbooks`"
            target="_blank"
          />
          <c-button
            secondary
            label="获取分卷信息"
            @action="populateNovelFromAmazon('', true)"
          />
          <c-button
            v-if="atLeastMaintainer"
            type="error"
            secondary
            label="标记重复"
            @action="markAsDuplicate"
          />
        </n-flex>
      </n-flex>
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

    <section-header title="标签">
      <c-button label="使用说明" @action="showKeywordsModal = true" />
    </section-header>

    <n-p>
      <n-text type="error"> 编辑标签前务必先看一遍使用说明。 </n-text>
    </n-p>

    <n-list
      v-if="presetKeywords.groups.length > 0"
      bordered
      style="width: 100%"
    >
      <n-list-item v-for="group of presetKeywords.groups" :key="group.title">
        <n-p>
          <n-flex style="margin-top: 8px">
            <n-tag :bordered="false" size="small">
              <b>{{ group.title }}</b>
            </n-tag>
            <n-tag
              v-for="keyword of group.presetKeywords"
              size="small"
              checkable
              :checked="formValue.keywords.includes(keyword)"
              @update:checked="(checked: boolean) => togglePresetKeyword(checked, keyword)"
            >
              {{ keyword }}
            </n-tag>
          </n-flex>
        </n-p>
      </n-list-item>
    </n-list>
    <n-p v-else>R18标签暂时不支持。</n-p>

    <section-header title="分卷" />
    <n-image-group show-toolbar-tooltip>
      <n-list>
        <n-list-item v-for="(volume, index) in formValue.volumes">
          <n-flex :wrap="false">
            <div>
              <n-image
                width="104"
                :src="volume.cover"
                :preview-src="volume.coverHires ?? volume.cover"
                :alt="volume.asin"
                lazy
                style="border-radius: 2px"
              />
            </div>

            <n-flex vertical style="flex: auto">
              <n-flex align="center" :size="0" :wrap="false">
                <n-text style="word-break: keep-all">日文标题：</n-text>
                <n-input
                  v-model:value="volume.title"
                  placeholder="日文标题"
                  :input-props="{ spellcheck: false }"
                />
              </n-flex>

              <n-text>
                ASIN：
                <n-a :href="`https://www.amazon.co.jp/zh/dp/${volume.asin}`">
                  {{ volume.asin }}
                </n-a>
              </n-text>
              <n-text>封面-缩略：{{ volume.cover }}</n-text>
              <n-text>封面-高清：{{ volume.coverHires }}</n-text>
              <n-text>
                出版：
                {{ volume.publisher ?? '未知出版商' }}
                /
                {{ volume.imprint ?? '未知文库' }}
                /
                <n-time
                  v-if="volume.publishAt"
                  :time="volume.publishAt * 1000"
                  type="date"
                />
              </n-text>

              <n-flex>
                <c-button
                  label="上移"
                  secondary
                  @action="moveVolumeUp(index)"
                />
                <c-button
                  label="下移"
                  secondary
                  @action="moveVolumeDown(index)"
                />
                <c-button
                  label="置顶"
                  secondary
                  @action="topVolume(volume.asin)"
                />
                <c-button
                  label="置底"
                  secondary
                  @action="bottomVolume(volume.asin)"
                />
                <c-button
                  label="删除"
                  secondary
                  type="error"
                  @action="deleteVolume(volume.asin)"
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
      require-login
      size="large"
      type="primary"
      class="float"
      @action="submit"
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
          require-login
          type="primary"
          :disabled="submitCurrentStep !== 2"
          @action="submit"
        />
      </n-step>
    </n-steps>
  </div>

  <c-modal title="使用说明" v-model:show="showKeywordsModal">
    <n-p>
      标签的意义在于辅助搜索。一个标签是否合适，要看标签相关情节的比例。仅仅是存在相关情节，不足以作为添加标签的依据。在实际操作中，可以思考搜索该标签的用户想不想看到这本书。
    </n-p>
    <n-p>
      下面是一些标签的具体解释。注意，同一个标签在一般向和R18下可能存在区别。
    </n-p>
    <n-divider />
    <n-p v-for="row of presetKeywords.explanations">
      <b>{{ row.word }}</b>
      <br />
      {{ row.explanation }}
    </n-p>
  </c-modal>
</template>
