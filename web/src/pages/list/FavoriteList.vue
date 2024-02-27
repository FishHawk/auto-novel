<script lang="ts" setup>
import { FormatListBulletedOutlined, PlusOutlined } from '@vicons/material';
import { MenuOption, useMessage } from 'naive-ui';
import { computed, h, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ApiUser, FavoredList } from '@/data/api/api_user';
import { WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { WenkuNovelOutlineDto } from '@/data/api/api_wenku_novel';
import { Page } from '@/data/api/common';
import { mapOk } from '@/data/result';
import {
  buildWebTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { useIsWideScreen } from '@/data/util';

import FavoriteMenuItem from './components/FavoriteMenuItem.vue';
import { Loader } from './components/NovelList.vue';
import NovelListWeb from './components/NovelListWeb.vue';

const isWideScreen = useIsWideScreen(850);

const route = useRoute();
const router = useRouter();
const message = useMessage();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();

const favoriteType = computed(() => (route.query.type ?? 'web') as string);
const favoriteId = computed(() => (route.query.fid ?? 'default') as string);

const options = [
  {
    label: '排序',
    tags: ['更新时间', '收藏时间'],
  },
];

const loader: Loader<
  | (Page<WebNovelOutlineDto> & { type: 'web' })
  | (Page<WenkuNovelOutlineDto> & { type: 'wenku' })
> = (page, _query, selected) => {
  function optionNth(n: number): string {
    return options[n].tags[selected[n]];
  }
  function optionSort() {
    const option = optionNth(0);
    if (option === '更新时间') {
      return 'update';
    } else {
      return 'create';
    }
  }

  if (favoriteType.value === 'web') {
    return ApiUser.listFavoredWebNovel(favoriteId.value, {
      page,
      pageSize: 30,
      sort: optionSort(),
    }).then((result) => mapOk(result, (page) => ({ type: 'web', ...page })));
  } else {
    return ApiUser.listFavoredWenkuNovel(favoriteId.value, {
      page,
      pageSize: 24,
      sort: optionSort(),
    }).then((result) => mapOk(result, (page) => ({ type: 'wenku', ...page })));
  }
};

const favoredList = ref<FavoredList>({
  web: [{ id: 'default', title: '默认收藏夹' }],
  wenku: [{ id: 'default', title: '默认收藏夹' }],
});

const loadFavoredList = async () => {
  const result = await ApiUser.listFavored();
  if (result.ok) {
    favoredList.value = result.value;
    const ids = (
      favoriteType.value === 'web' ? result.value.web : result.value.wenku
    ).map((it) => it.id);
    if (!ids.includes(favoriteId.value)) {
      router.push({ path: `favorite?type=${favoriteType.value}` });
    }
  } else {
    message.error('收藏夹加载失败:' + result.error.message);
  }
};
loadFavoredList();

const favoriteMenuOption = (
  type: 'web' | 'wenku',
  id: string,
  title: string
): MenuOption => ({
  label: () =>
    h(FavoriteMenuItem, {
      id,
      title,
      type,
      onUpdated: loadFavoredList,
      onDeleted: loadFavoredList,
    }),
  key: type + id,
});

const currentMenuKey = computed(() => favoriteType.value + favoriteId.value);
const menuOptions = computed(() => [
  {
    type: 'group',
    label: '网络小说',
    children: favoredList.value.web.map(({ id, title }) =>
      favoriteMenuOption('web', id, title)
    ),
  },
  {
    type: 'divider',
    key: 'divider',
    props: { style: { marginLeft: '32px' } },
  },
  {
    type: 'group',
    label: '文库小说',
    children: favoredList.value.wenku.map(({ id, title }) =>
      favoriteMenuOption('wenku', id, title)
    ),
  },
]);

const showListModal = ref(false);
const showAddModal = ref(false);
const showOperationPanel = ref(false);

const novelListWebRef = ref<InstanceType<typeof NovelListWeb>>();

const queueOrder = ref<'asc' | 'desc'>('desc');
const queueOrderOptions = [
  { value: 'desc', label: '从新到旧' },
  { value: 'asc', label: '从旧到新' },
];

const queueTaskSize = ref<'full' | 'first5'>('full');
const queueTaskSizeOptions = [
  { value: 'full', label: '全部' },
  { value: 'first5', label: '前5话' },
];

const submitJob = (id: 'gpt' | 'sakura') => {
  const novels = novelListWebRef.value?.$props.items;
  if (novels === undefined || novels.length === 0) {
    message.error('本页无小说');
  } else {
    const novelsSorted =
      queueOrder.value === 'desc' ? novels : novels.slice().reverse();
    const end = queueTaskSize.value === 'full' ? 65535 : 5;

    novelsSorted.forEach((it) => {
      const task = buildWebTranslateTask(it.providerId, it.novelId, {
        start: 0,
        end: end,
        expire: false,
      });
      const workspace = id === 'gpt' ? gptWorkspace : sakuraWorkspace;
      workspace.addJob({
        task,
        description: it.titleJp,
        createAt: Date.now(),
      });
    });
    message.success('排队成功');
  }
};
</script>

<template>
  <c-layout :sidebar="isWideScreen" :sidebar-width="320" class="layout-content">
    <div style="flex: auto">
      <n-h1>我的收藏</n-h1>
      <n-flex style="margin-bottom: 24px">
        <c-button
          label="批量操作"
          @click="showOperationPanel = !showOperationPanel"
        />
        <c-button
          v-if="!isWideScreen"
          label="收藏夹列表"
          :icon="FormatListBulletedOutlined"
          @click="showListModal = true"
        />
      </n-flex>

      <n-collapse-transition
        :show="showOperationPanel"
        style="margin-bottom: 16px"
      >
        <n-list bordered>
          <n-list-item>目前不支持选择小说，只能控制整页。</n-list-item>

          <n-list-item>
            <c-button size="small" label="移动（暂未实现）" />
          </n-list-item>

          <n-list-item v-if="favoriteType === 'web'">
            <n-flex vertical>
              <b>批量生成GPT/Sakura任务</b>

              <n-flex align="baseline" :wrap="false">
                <n-text style="white-space: nowrap">语言</n-text>
                <n-radio-group v-model:value="queueOrder" size="small">
                  <n-radio-button
                    v-for="option in queueOrderOptions"
                    :key="option.value"
                    :value="option.value"
                    :label="option.label"
                  />
                </n-radio-group>
              </n-flex>

              <n-flex align="baseline" :wrap="false">
                <n-text style="white-space: nowrap">范围</n-text>
                <n-radio-group v-model:value="queueTaskSize" size="small">
                  <n-radio-button
                    v-for="option in queueTaskSizeOptions"
                    :key="option.value"
                    :value="option.value"
                    :label="option.label"
                  />
                </n-radio-group>
              </n-flex>

              <n-flex align="baseline" :wrap="false">
                <n-text style="white-space: nowrap">操作</n-text>
                <n-button-group size="small">
                  <c-button
                    label="排队GPT"
                    :round="false"
                    @click="submitJob('gpt')"
                  />
                  <c-button
                    label="排队Sakura"
                    :round="false"
                    @click="submitJob('sakura')"
                  />
                </n-button-group>
              </n-flex>
            </n-flex>
          </n-list-item>
        </n-list>
      </n-collapse-transition>

      <NovelList :options="options" :loader="loader" v-slot="{ page }">
        <NovelListWeb
          v-if="page.type === 'web'"
          ref="novelListWebRef"
          :items="page.items"
          simple
        />
        <NovelListWenku v-if="page.type === 'wenku'" :items="page.items" />
      </NovelList>
    </div>
    <template #sidebar>
      <section-header title="收藏夹">
        <c-button
          label="添加"
          :icon="PlusOutlined"
          @click="showAddModal = true"
        />
      </section-header>
      <n-menu :value="currentMenuKey" :options="menuOptions" />
    </template>
  </c-layout>

  <n-drawer
    v-model:show="showListModal"
    width="320"
    :auto-focus="false"
    placement="right"
  >
    <n-drawer-content
      :native-scrollbar="false"
      :scrollbar-props="{ trigger: 'none' }"
    >
      <section-header title="收藏夹">
        <c-button
          label="添加"
          :icon="PlusOutlined"
          @click="showAddModal = true"
        />
      </section-header>
      <n-menu :value="currentMenuKey" :options="menuOptions" />
    </n-drawer-content>
  </n-drawer>

  <favorite-add-modal v-model:show="showAddModal" @created="loadFavoredList" />
</template>
