<script lang="ts" setup generic="T extends any">
import { v4 as uuidv4 } from 'uuid';

import { Page } from '@/model/Page';
import { Result } from '@/util/result';

import { onKeyDown } from '../util';

export type Loader<T extends any> = (page: number) => Promise<Result<Page<T>>>;

const route = useRoute();
const router = useRouter();

const props = defineProps<{
  page: number;
  loader: Loader<T>;
}>();

const pageNumber = ref(1);
const pageContent = ref<Result<Page<T>>>();

const loadId = ref(uuidv4());

const reload = async () => {
  const currentLoadId = uuidv4();
  loadId.value = currentLoadId;

  pageContent.value = undefined;
  const result = await props.loader(props.page - 1);

  if (loadId.value !== currentLoadId) return;

  pageContent.value = result;
  if (result.ok) {
    pageNumber.value = result.value.pageNumber;
  }
};

const onUpdatePage = (page: number) => {
  const query = { ...route.query, page };
  router.push({ path: route.path, query });
};

watch(
  () => props.page,
  async () => reload(),
  { immediate: true },
);

watch(
  () => props.loader,
  () => {
    pageNumber.value = 1;
    if (props.page > 1) {
      onUpdatePage(1);
    } else {
      reload();
    }
  },
);

onKeyDown('ArrowLeft', (e) => {
  if (pageContent.value === undefined) return;
  const page = props.page;
  if (page > 1) {
    // hacky:防止在编辑搜索栏时跳转
    if (e.target instanceof Element && e.target.tagName === 'INPUT') {
      return;
    }
    onUpdatePage(page - 1);
    e.preventDefault();
  }
});

onKeyDown('ArrowRight', (e) => {
  if (pageContent.value === undefined) return;
  const page = props.page;
  if (page < pageNumber.value) {
    // hacky:防止在编辑搜索栏时跳转
    if (e.target instanceof Element && e.target.tagName === 'INPUT') {
      return;
    }
    onUpdatePage(page + 1);
    e.preventDefault();
  }
});
</script>

<template>
  <n-pagination
    v-if="pageNumber > 1"
    :page="page"
    @update-page="(page) => onUpdatePage(page)"
    :page-count="pageNumber"
    :page-slot="7"
    style="margin-top: 20px"
  />
  <n-divider />

  <c-result
    :result="pageContent"
    :show-empty="(it: Page<T>) => it.items.length === 0"
    v-slot="{ value: page }"
  >
    <slot :items="page.items" />
  </c-result>

  <n-divider />
  <n-pagination
    v-if="pageNumber > 1"
    :page="page"
    @update-page="(page) => onUpdatePage(page)"
    :page-count="pageNumber"
    :page-slot="7"
  />
</template>
