<script lang="ts" setup generic="T extends any">
import { SyncAltOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { Page } from '@/model/Page';
import { Result } from '@/util/result';
import { RegexUtil } from '@/util';

export type Loader<T> = (
  page: number,
  query: string,
  selected: number[],
) => Promise<Result<Page<T>>>;

const props = defineProps<{
  page: number;
  query?: string;
  selected?: number[];
  loader: Loader<T>;
  search?: { suggestions: string[]; tags: string[] };
  options: { label: string; tags: string[]; multiple?: boolean }[];
}>();

const route = useRoute();
const router = useRouter();

const processQueryWithLocaleAware = (input: string): string => {
  const cc = Locator.settingRepository().cc;
  const queries = input.split(/( |\||\+|\-|\")/);
  const result: string[] = [];
  for (const query of queries) {
    if (
      !query.includes('$') &&
      !RegexUtil.hasKanaChars(query) &&
      !RegexUtil.hasHangulChars(query) &&
      RegexUtil.hasHanzi(query)
    ) {
      const queryData = cc.value.toData(query);
      if (queryData === query) {
        result.push(query);
      } else {
        result.push(`(${[query, queryData].join('|')})`);
      }
    } else {
      result.push(query);
    }
  }
  return result.join('');
};

const loader = computed(() => {
  const loaderOut = props.loader;

  let query = (props.query ?? '').trim();
  const setting = Locator.settingRepository().setting;
  if (setting.value.searchLocaleAware) {
    query = processQueryWithLocaleAware(query);
  }
  const selected = selectedWithDefault.value;

  return (page: number) => {
    return loaderOut(page, query, selected);
  };
});

const queryEdit = ref(props.query ?? '');
watch(
  () => props.query,
  (query) => {
    queryEdit.value = props.query ?? '';
  },
);

const onUpdateQuery = (query: string) => {
  queryEdit.value = query;
  router.push({
    path: route.path,
    query: { ...route.query, query, page: 1 },
  });
};

const selectedWithDefault = ref<number[]>([]);

watch(
  props,
  ({ options, selected }) => {
    const newSelected = options.map(({ tags, multiple }, index) => {
      const defaultSelected = multiple ? 2 ** tags.length - 1 : 0;
      return selected?.[index] ?? defaultSelected;
    });
    const isEqual = (a: number[], b: number[]) => {
      if (a.length !== b.length) return false;
      for (const [i, av] of a.entries()) {
        const bv = b[i];
        if (av !== bv) return false;
      }
      return true;
    };
    if (!isEqual(newSelected, selectedWithDefault.value)) {
      selectedWithDefault.value = newSelected;
    }
  },
  { immediate: true },
);

const onUpdateSelect = (
  optionIndex: number,
  index: number,
  multiple: boolean = false,
) => {
  const selected = [...selectedWithDefault.value];
  if (!multiple) {
    selected[optionIndex] = index;
  } else {
    selected[optionIndex] ^= 1 << index;
  }
  router.push({
    path: route.path,
    query: { ...route.query, selected, page: 1 },
  });
};

const isSelected = (
  optionIndex: number,
  index: number,
  multiple: boolean = false,
) => {
  if (!multiple) {
    return index === selectedWithDefault.value[optionIndex];
  } else {
    return (selectedWithDefault.value[optionIndex] & (1 << index)) != 0;
  }
};

const invertSelection = (optionIndex: number) => {
  const option = props.options[optionIndex];
  if (option.multiple === true) {
    const selected = [...selectedWithDefault.value];
    selected[optionIndex] ^= 2 ** option.tags.length - 1;
    router.push({
      path: route.path,
      query: { ...route.query, selected, page: 1 },
    });
  }
};
</script>

<template>
  <n-flex
    v-if="search !== undefined || options.length >= 0"
    size="large"
    vertical
    style="width: 100%; margin-top: 8px"
  >
    <c-action-wrapper v-if="search !== undefined" title="搜索" size="large">
      <input-with-suggestion
        v-model:value="queryEdit"
        :suggestions="search.suggestions"
        :tags="search.tags"
        :placeholder="`中/日文标题或作者`"
        style="flex: 0 1 400px; margin-right: 8px"
        :input-props="{ spellcheck: false }"
        @select="onUpdateQuery"
      />
    </c-action-wrapper>
    <c-action-wrapper
      v-for="(option, optionIndex) in options"
      :key="option.label"
      :title="option.label"
      align="baseline"
      size="large"
    >
      <n-flex :size="[16, 4]">
        <n-text
          v-for="(tag, index) in option.tags"
          :key="tag"
          text
          :type="
            isSelected(optionIndex, index, option.multiple)
              ? 'primary'
              : 'default'
          "
          @click="onUpdateSelect(optionIndex, index, option.multiple)"
          style="cursor: pointer"
        >
          {{ tag }}
        </n-text>

        <c-button
          v-if="option.multiple"
          :icon="SyncAltOutlined"
          type="primary"
          text
          @click="invertSelection(optionIndex)"
        />
      </n-flex>
    </c-action-wrapper>
  </n-flex>

  <c-page :page="page" :loader="loader" v-slot="{ items }">
    <slot :items="items" />
  </c-page>
</template>
