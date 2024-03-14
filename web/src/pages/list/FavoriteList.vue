<script lang="ts" setup>
import {
  FormatListBulletedOutlined,
  ChecklistOutlined,
  PlusOutlined,
} from '@vicons/material';
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
import { useIsWideScreen } from '@/pages/util';

import FavoriteMenuItem from './components/FavoriteMenuItem.vue';
import { Loader } from './components/NovelList.vue';
import NovelListWeb from './components/NovelListWeb.vue';
import NovelListWenku from './components/NovelListWenku.vue';

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
const novelListWenkuRef = ref<InstanceType<typeof NovelListWenku>>();

const selectedSize = computed(() => {
  if (favoriteType.value === 'web') {
    return novelListWebRef.value?.getSelectedNovels()?.length ?? 0;
  } else {
    return novelListWenkuRef.value?.getSelectedNovels()?.length ?? 0;
  }
});

const getSelectedNovels = () => {
  if (favoriteType.value === 'web') {
    const novels = novelListWebRef.value?.getSelectedNovels();
    if (novels !== undefined && novels.length > 0) {
      return { type: 'web' as 'web', novels };
    }
  } else {
    const novels = novelListWenkuRef.value?.getSelectedNovels();
    if (novels !== undefined && novels.length > 0) {
      return { type: 'wenku' as 'wenku', novels };
    }
  }
  message.info('没有选中小说');
  return undefined;
};
const selectAll = () => {
  if (favoriteType.value === 'web') {
    novelListWebRef.value?.selectAll();
  } else {
    novelListWenkuRef.value?.selectAll();
  }
};
const invertSelection = () => {
  if (favoriteType.value === 'web') {
    novelListWebRef.value?.invertSelection();
  } else {
    novelListWenkuRef.value?.invertSelection();
  }
};

const targetFavoredId = ref<string>(favoriteId.value);

const moveToFavored = async () => {
  const selectedNovels = getSelectedNovels();
  if (selectedNovels === undefined) return;
  if (targetFavoredId.value === favoriteId.value) {
    message.info('无需移动');
    return;
  }

  if (selectedNovels.type === 'web') {
    for (const novel of selectedNovels.novels) {
      const result = await (targetFavoredId.value !== 'deleted'
        ? ApiUser.favoriteWebNovel(
            targetFavoredId.value,
            novel.providerId,
            novel.novelId
          )
        : ApiUser.unfavoriteWebNovel(
            favoriteId.value,
            novel.providerId,
            novel.novelId
          ));
      if (!result.ok) {
        message.error(`收藏错误： ${novel.titleJp}\n` + result.error.message);
      }
    }
  } else {
    for (const novel of selectedNovels.novels) {
      const result = await (targetFavoredId.value !== 'deleted'
        ? ApiUser.favoriteWenkuNovel(targetFavoredId.value, novel.id)
        : ApiUser.unfavoriteWenkuNovel(targetFavoredId.value, novel.id));
      if (!result.ok) {
        message.error(
          `取消收藏错误： ${novel.titleZh}\n` + result.error.message
        );
      }
    }
  }
  message.info('移动完成');
  window.location.reload();
};

const queueOrder = ref<'asc' | 'desc'>('desc');
const queueOrderOptions = [
  { value: 'desc', label: '从新到旧' },
  { value: 'asc', label: '从旧到新' },
];

const queueTaskSize = ref<'first5' | 'full' | 'full-expire'>('full');
const queueTaskSizeOptions = [
  { value: 'first5', label: '前5话' },
  { value: 'full', label: '全部' },
  { value: 'full-expire', label: '全部+过期章节' },
];

const submitJob = (id: 'gpt' | 'sakura') => {
  const selectedNovels = getSelectedNovels();
  if (selectedNovels === undefined || selectedNovels.type === 'wenku') return;

  const novelsSorted =
    queueOrder.value === 'desc'
      ? selectedNovels.novels
      : selectedNovels.novels.slice().reverse();
  const end = queueTaskSize.value === 'first5' ? 5 : 65535;
  const expire = queueTaskSize.value === 'full-expire';

  novelsSorted.forEach((it) => {
    const task = buildWebTranslateTask(it.providerId, it.novelId, {
      start: 0,
      end,
      expire,
      toc: false,
    });
    const workspace = id === 'gpt' ? gptWorkspace : sakuraWorkspace;
    workspace.addJob({
      task,
      description: it.titleJp,
      createAt: Date.now(),
    });
  });
  message.success('排队成功');
};
</script>

<template>
  <c-layout :sidebar="isWideScreen" :sidebar-width="320" class="layout-content">
    <div style="flex: auto">
      <n-h1>我的收藏</n-h1>
      <n-flex style="margin-bottom: 24px">
        <c-button
          label="批量操作"
          :icon="ChecklistOutlined"
          @action="showOperationPanel = !showOperationPanel"
        />
        <c-button
          v-if="!isWideScreen"
          label="收藏夹"
          :icon="FormatListBulletedOutlined"
          @action="showListModal = true"
        />
      </n-flex>

      <n-collapse-transition
        :show="showOperationPanel"
        style="margin-bottom: 16px"
      >
        <n-list bordered>
          <n-list-item>
            <c-action-wrapper title="选择">
              <n-flex align="baseline">
                <n-button-group size="small">
                  <c-button label="全选" :round="false" @action="selectAll" />
                  <c-button
                    label="反选"
                    :round="false"
                    @action="invertSelection"
                  />
                </n-button-group>
                <n-text depth="3"> 已选择{{ selectedSize }}本小说 </n-text>
              </n-flex>
            </c-action-wrapper>
          </n-list-item>

          <n-list-item>
            <n-flex vertical>
              <b>批量移动小说（低配版，很慢，等到显示移动完成）</b>

              <c-action-wrapper title="目标">
                <n-radio-group v-model:value="targetFavoredId">
                  <n-flex>
                    <n-radio
                      v-for="favored of favoriteType === 'web'
                        ? favoredList.web
                        : favoredList.wenku"
                      :key="favored.id"
                      :value="favored.id"
                    >
                      {{ favored.title }}
                    </n-radio>
                    <n-radio key="deleted" value="deleted"> 取消收藏 </n-radio>
                  </n-flex>
                </n-radio-group>
              </c-action-wrapper>

              <c-action-wrapper title="操作">
                <c-button
                  label="移动"
                  size="small"
                  :round="false"
                  @action="moveToFavored"
                />
              </c-action-wrapper>
            </n-flex>
          </n-list-item>

          <n-list-item v-if="favoriteType === 'web'">
            <n-flex vertical>
              <b>批量生成GPT/Sakura任务</b>

              <c-action-wrapper title="顺序">
                <c-radio-group
                  v-model:value="queueOrder"
                  :options="queueOrderOptions"
                  size="small"
                />
              </c-action-wrapper>

              <c-action-wrapper title="范围">
                <c-radio-group
                  v-model:value="queueTaskSize"
                  :options="queueTaskSizeOptions"
                  size="small"
                />
              </c-action-wrapper>

              <c-action-wrapper title="操作">
                <n-button-group size="small">
                  <c-button
                    label="排队GPT"
                    :round="false"
                    @action="submitJob('gpt')"
                  />
                  <c-button
                    label="排队Sakura"
                    :round="false"
                    @action="submitJob('sakura')"
                  />
                </n-button-group>
              </c-action-wrapper>
            </n-flex>
          </n-list-item>
        </n-list>
      </n-collapse-transition>

      <NovelList :options="options" :loader="loader" v-slot="{ page }">
        <NovelListWeb
          v-if="page.type === 'web'"
          ref="novelListWebRef"
          :items="page.items"
          :selectable="showOperationPanel"
          simple
        />
        <NovelListWenku
          v-if="page.type === 'wenku'"
          ref="novelListWenkuRef"
          :items="page.items"
          :selectable="showOperationPanel"
        />
      </NovelList>
    </div>
    <template #sidebar>
      <section-header title="收藏夹">
        <c-button
          label="添加"
          :icon="PlusOutlined"
          @action="showAddModal = true"
        />
      </section-header>
      <n-menu :value="currentMenuKey" :options="menuOptions" />
    </template>
  </c-layout>

  <c-drawer-right
    v-if="!isWideScreen"
    v-model:show="showListModal"
    title="收藏夹"
  >
    <template #action>
      <c-button
        label="添加"
        :icon="PlusOutlined"
        @action="showAddModal = true"
      />
    </template>
    <n-menu :value="currentMenuKey" :options="menuOptions" />
  </c-drawer-right>

  <favorite-add-modal v-model:show="showAddModal" @created="loadFavoredList" />
</template>
