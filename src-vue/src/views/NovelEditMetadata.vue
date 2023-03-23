<script lang="ts" setup>
import { onMounted, Ref, ref, shallowRef } from 'vue';
import { useRoute } from 'vue-router';
import { useMessage } from 'naive-ui';
import { TocFilled, UploadFilled } from '@vicons/material';

import { Ok, ResultState } from '../data/api/result';
import ApiNovel from '../data/api/api_novel';
import ApiNovelEdit from '../data/api/api_patch';
import { buildMetadataUrl } from '../data/provider';
import { errorToString } from '../data/handle_error';

interface EditField {
  jp: string;
  zh?: string;
  ref: Ref<string | undefined>;
}

interface EditGlossary {
  origin: { [key: string]: string };
  ref: Ref<{ [key: string]: string }>;
}

interface EditMetadata {
  title: EditField;
  introduction: EditField;
  glossary: EditGlossary;
  toc: EditField[];
}

const route = useRoute();
const providerId = route.params.providerId as string;
const bookId = route.params.bookId as string;
const url = buildMetadataUrl(providerId, bookId);

const message = useMessage();

const editMetadataRef = shallowRef<ResultState<EditMetadata>>();
const termsToAdd = ref(['', '']);

onMounted(() => getMetadata());
async function getMetadata() {
  const result = await ApiNovel.getMetadata(providerId, bookId, undefined);
  if (result.ok) {
    document.title = `编辑 - ${result.value.titleJp}`;
    const bookMetadata = result.value;
    const tocSet = new Set();
    const editMetadata: EditMetadata = {
      title: {
        jp: bookMetadata.titleJp,
        zh: bookMetadata.titleZh,
        ref: ref(bookMetadata.titleZh),
      },
      introduction: {
        jp: bookMetadata.introductionJp,
        zh: bookMetadata.introductionZh,
        ref: ref(bookMetadata.introductionZh),
      },
      glossary: {
        origin: bookMetadata.glossary,
        ref: ref(bookMetadata.glossary),
      },
      toc: bookMetadata.toc
        .filter((item) => {
          const inTocSet = tocSet.has(item.titleJp);
          if (!inTocSet) tocSet.add(item.titleJp);
          return !inTocSet;
        })
        .map((item) => ({
          jp: item.titleJp,
          zh: item.titleZh,
          ref: ref(item.titleZh),
        })),
    };
    editMetadataRef.value = Ok(editMetadata);
  } else {
    editMetadataRef.value = result;
  }
}

async function submitTranslate() {
  if (!editMetadataRef.value?.ok) return;
  const editMetadata = editMetadataRef.value.value;
  const patch = {
    title:
      editMetadata.title.ref.value != editMetadata.title.zh
        ? editMetadata.title.ref.value
        : undefined,
    introduction:
      editMetadata.introduction.ref.value != editMetadata.introduction.zh
        ? editMetadata.introduction.ref.value
        : undefined,
    glossary: editMetadata.glossary.ref.value,
    toc: Object.assign(
      {},
      ...editMetadata.toc
        .filter((item) => item.ref.value != item.zh)
        .map((item) => ({ [item.jp]: item.ref.value }))
    ),
  };
  const result = await ApiNovelEdit.postMetadataPatch(
    providerId,
    bookId,
    patch
  );
  if (result.ok) {
    message.success('提交成功');
  } else {
    message.error('提交失败：' + errorToString(result.error));
  }
}

function deleteTerm(jp: string) {
  if (editMetadataRef.value?.ok) {
    delete editMetadataRef.value.value.glossary.ref.value[jp];
    editMetadataRef.value.value.glossary = editMetadataRef.value.value.glossary;
  }
}

function addTerm() {
  if (editMetadataRef.value?.ok) {
    const jp = termsToAdd.value[0];
    const zh = termsToAdd.value[1];
    if (jp && zh) {
      editMetadataRef.value.value.glossary.ref.value[jp] = zh;
      termsToAdd.value = ['', ''];
    }
  }
}
</script>

<template>
  <MainLayout>
    <div v-if="editMetadataRef?.ok">
      <n-h2 prefix="bar">
        <n-a :href="url" target="_blank">{{
          editMetadataRef.value.title.jp
        }}</n-a>
        <br />
        <span style="color: grey">{{ editMetadataRef.value.title.zh }}</span>
      </n-h2>

      <n-a :href="`/novel/${providerId}/${bookId}`">
        <n-button>
          <template #icon>
            <n-icon> <TocFilled /> </n-icon>
          </template>
          返回目录
        </n-button>
      </n-a>

      <n-p>{{ editMetadataRef.value.title.jp }}</n-p>
      <n-input
        v-model:value="editMetadataRef.value.title.ref.value"
        :placeholder="editMetadataRef.value.title.jp"
      />

      <n-p>{{ editMetadataRef.value.introduction.jp }}</n-p>
      <n-input
        v-model:value="editMetadataRef.value.introduction.ref.value"
        :placeholder="editMetadataRef.value.introduction.jp"
        type="textarea"
      />

      <n-h2 prefix="bar">术语表</n-h2>
      <n-p>在你使用术语表之前需要知道的：</n-p>
      <ui>
        <li>
          <span>
            术语表的原理是在翻译前将日文词替换成随机字母，在翻译后替换回对应中文词。
          </span>
        </li>
        <li>
          <span>
            在修改术语表后再次更新中文时，已经翻译的章节会按照新的术语表重新翻译需要更新的段落。
          </span>
        </li>
        <li>
          <span>
            术语表会影响ai对词义的理解，例如：无法从人名判断性别导致ai使用了错误的人称代词。
          </span>
        </li>
      </ui>
      <n-p> 总而言之,术语表不是万能的，请只在有必要的情况下编辑术语表。 </n-p>
      <table style="border-spacing: 16px 0px">
        <tr
          v-for="(termZh, termJp) in editMetadataRef.value.glossary.ref.value"
        >
          <td>{{ termJp }}</td>
          <td style="width: 4px">=></td>
          <td>{{ termZh }}</td>
          <td>
            <n-button @click="deleteTerm(termJp as string)">删除</n-button>
          </td>
        </tr>
        <tr>
          <td colspan="3">
            <n-input
              pair
              v-model:value="termsToAdd"
              separator="=>"
              :placeholder="['日文', '中文']"
              clearable
            />
          </td>
          <td>
            <n-button @click="addTerm()">添加</n-button>
          </td>
        </tr>
      </table>

      <n-h2 prefix="bar">目录</n-h2>
      <table style="width: 100%">
        <template v-for="token in editMetadataRef.value.toc">
          <tr>
            <td style="width: 50%; padding: 4px">
              {{ token.jp }}
              <br />
              <n-input
                class="on-mobile"
                v-model:value="token.ref.value"
                :placeholder="token.jp"
              />
            </td>
            <td class="on-desktop" style="padding: 4px">
              <n-input
                v-model:value="token.ref.value"
                :placeholder="token.jp"
              />
            </td>
          </tr>
          <n-divider class="on-desktop" style="width: 200%; margin: 0px" />
          <n-divider class="on-mobile" style="width: 100%; margin: 0px" />
        </template>
      </table>

      <n-button
        round
        size="large"
        type="primary"
        class="float"
        @click="submitTranslate()"
      >
        <template #icon>
          <n-icon><UploadFilled /></n-icon>
        </template>
        提交翻译
      </n-button>
    </div>

    <div v-if="editMetadataRef && !editMetadataRef.ok">
      <n-result
        status="error"
        title="加载错误"
        :description="errorToString(editMetadataRef.error)"
      />
    </div>
  </MainLayout>
</template>

<style scoped>
.float {
  position: fixed;
  right: 40px;
  bottom: 40px;
  box-shadow: rgb(0 0 0 / 12%) 0px 2px 8px 0px;
}
</style>
