<script lang="ts" setup>
import {
  ChecklistOutlined,
  FormatListBulletedOutlined,
  PlusOutlined,
} from '@vicons/material';
import { useKeyModifier } from '@vueuse/core';
import { MenuOption } from 'naive-ui';

import { Locator } from '@/data';
import { UserRepository } from '@/data/api';
import { TranslateTaskDescriptor } from '@/model/Translator';
import { FavoredList } from '@/model/User';
import { WebNovelOutlineDto } from '@/model/WebNovel';
import { WenkuNovelOutlineDto } from '@/model/WenkuNovel';
import { doAction, useIsWideScreen } from '@/pages/util';
import { runCatching } from '@/util/result';

import FavoriteMenuItem from './components/FavoriteMenuItem.vue';
import NovelListWeb from './components/NovelListWeb.vue';
import NovelListWenku from './components/NovelListWenku.vue';
import { Loader } from './components/NovelPage.vue';

const props = defineProps<{
  page: number;
  selected: number[];
  favoriteType: 'web' | 'wenku';
  favoriteId: string;
}>();

const isWideScreen = useIsWideScreen(850);
const router = useRouter();
const message = useMessage();

const options = [
  {
    label: '排序',
    tags: ['更新时间', '收藏时间'],
  },
];

const loaderWeb = computed<Loader<WebNovelOutlineDto>>(() => {
  const { favoriteId } = props;
  return (page, _query, selected) => {
    const optionNth = (n: number): string => options[n].tags[selected[n]];
    const optionSort = () => {
      const option = optionNth(0);
      if (option === '更新时间') {
        return 'update';
      } else {
        return 'create';
      }
    };
    return runCatching(
      UserRepository.listFavoredWebNovel(favoriteId, {
        page,
        pageSize: 30,
        sort: optionSort(),
      }).then((it) => ({ type: 'web', ...it })),
    );
  };
});

const loaderWenku = computed<Loader<WenkuNovelOutlineDto>>(() => {
  const { favoriteId } = props;
  return (page, _query, selected) => {
    const optionNth = (n: number): string => options[n].tags[selected[n]];
    const optionSort = () => {
      const option = optionNth(0);
      if (option === '更新时间') {
        return 'update';
      } else {
        return 'create';
      }
    };
    return runCatching(
      UserRepository.listFavoredWenkuNovel(favoriteId, {
        page,
        pageSize: 24,
        sort: optionSort(),
      }).then((it) => ({ type: 'wenku', ...it })),
    );
  };
});

const favoredList = ref<FavoredList>({
  web: [{ id: 'default', title: '默认收藏夹' }],
  wenku: [{ id: 'default', title: '默认收藏夹' }],
});

const loadFavoredList = async () => {
  const { favoriteType, favoriteId } = props;
  const result = await runCatching(UserRepository.listFavored());
  if (result.ok) {
    favoredList.value = result.value;
    const ids = (
      favoriteType === 'web' ? result.value.web : result.value.wenku
    ).map((it) => it.id);
    if (!ids.includes(favoriteId)) {
      router.push({ path: `favorite?type=${favoriteType}` });
    }
  } else {
    message.error('收藏夹加载失败:' + result.error.message);
  }
};
loadFavoredList();

const favoriteMenuOption = (
  type: 'web' | 'wenku',
  id: string,
  title: string,
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

const currentMenuKey = computed(() => props.favoriteType + props.favoriteId);
const menuOptions = computed(() => [
  {
    type: 'group',
    label: '网络小说',
    children: favoredList.value.web.map(({ id, title }) =>
      favoriteMenuOption('web', id, title),
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
      favoriteMenuOption('wenku', id, title),
    ),
  },
]);

const showListModal = ref(false);
const showAddModal = ref(false);
const showOperationPanel = ref(false);

const novelListWebRef = ref<InstanceType<typeof NovelListWeb>>();
const novelListWenkuRef = ref<InstanceType<typeof NovelListWenku>>();

const selectedSize = computed(() => {
  if (props.favoriteType === 'web') {
    return novelListWebRef.value?.getSelectedNovels()?.length ?? 0;
  } else {
    return novelListWenkuRef.value?.getSelectedNovels()?.length ?? 0;
  }
});

const getSelectedNovels = () => {
  if (props.favoriteType === 'web') {
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
  if (props.favoriteType === 'web') {
    novelListWebRef.value?.selectAll();
  } else {
    novelListWenkuRef.value?.selectAll();
  }
};
const invertSelection = () => {
  if (props.favoriteType === 'web') {
    novelListWebRef.value?.invertSelection();
  } else {
    novelListWenkuRef.value?.invertSelection();
  }
};

const displayKeywords = ref(false);

const targetFavoredId = ref<string>(props.favoriteId);

const moveToFavored = async () => {
  const selectedNovels = getSelectedNovels();
  if (selectedNovels === undefined) return;
  if (targetFavoredId.value === props.favoriteId) {
    message.info('无需移动');
    return;
  }

  if (selectedNovels.type === 'web') {
    for (const novel of selectedNovels.novels) {
      await doAction(
        targetFavoredId.value !== 'deleted'
          ? UserRepository.favoriteWebNovel(
              targetFavoredId.value,
              novel.providerId,
              novel.novelId,
            )
          : UserRepository.unfavoriteWebNovel(
              props.favoriteId,
              novel.providerId,
              novel.novelId,
            ),
        '收藏',
        message,
      );
    }
  } else {
    for (const novel of selectedNovels.novels) {
      await doAction(
        targetFavoredId.value !== 'deleted'
          ? UserRepository.favoriteWenkuNovel(targetFavoredId.value, novel.id)
          : UserRepository.unfavoriteWenkuNovel(
              targetFavoredId.value,
              novel.id,
            ),
        '取消收藏',
        message,
      );
    }
  }
  message.info('移动完成');
  window.location.reload();
};

const translateLevel = ref<'normal' | 'expire' | 'all'>('normal');
const forceMetadata = ref(false);
const first5 = ref(false);
const reverseOrder = ref(false);
const shouldTopJob = useKeyModifier('Control');

const submitJob = (id: 'gpt' | 'sakura') => {
  const selectedNovels = getSelectedNovels();
  if (selectedNovels === undefined || selectedNovels.type === 'wenku') return;

  const novelsSorted = reverseOrder.value
    ? selectedNovels.novels.slice().reverse()
    : selectedNovels.novels;

  novelsSorted.forEach((it) => {
    const task = TranslateTaskDescriptor.web(it.providerId, it.novelId, {
      startIndex: 0,
      endIndex: first5.value ? 5 : 65535,
      level: translateLevel.value,
      sync: false,
      forceMetadata: false,
    });
    const workspace =
      id === 'gpt'
        ? Locator.gptWorkspaceRepository()
        : Locator.sakuraWorkspaceRepository();
    const job = {
      task,
      description: it.titleJp,
      createAt: Date.now(),
    };
    workspace.addJob(job);
    if (shouldTopJob.value) {
      workspace.topJob(job);
    }
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
            <n-flex vertical>
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

              <c-action-wrapper
                v-if="favoriteType === 'web'"
                title="显示标签"
                align="center"
              >
                <n-switch v-model:value="displayKeywords" size="small" />
              </c-action-wrapper>
            </n-flex>
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

              <c-action-wrapper title="选项">
                <n-flex size="small">
                  <n-tooltip trigger="hover">
                    <template #trigger>
                      <n-flex :size="0" :wrap="false">
                        <tag-button
                          label="常规"
                          :checked="translateLevel === 'normal'"
                          @update:checked="translateLevel = 'normal'"
                        />
                        <tag-button
                          label="过期"
                          :checked="translateLevel === 'expire'"
                          @update:checked="translateLevel = 'expire'"
                        />
                        <tag-button
                          label="重翻"
                          type="warning"
                          :checked="translateLevel === 'all'"
                          @update:checked="translateLevel = 'all'"
                        />
                      </n-flex>
                    </template>
                    常规：只翻译未翻译的章节<br />
                    过期：翻译术语表过期的章节<br />
                    重翻：重翻全部章节<br />
                  </n-tooltip>

                  <tag-button
                    label="重翻目录"
                    v-model:checked="forceMetadata"
                  />
                  <tag-button label="前5话" v-model:checked="first5" />
                  <tag-button label="倒序添加" v-model:checked="reverseOrder" />

                  <n-text
                    v-if="translateLevel === 'all'"
                    type="warning"
                    style="font-size: 12px; flex-basis: 100%"
                  >
                    <b> * 请确保你知道自己在干啥，不要随便使用危险功能 </b>
                  </n-text>
                </n-flex>
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

      <novel-page
        v-if="favoriteType === 'web'"
        :page="page"
        :selected="selected"
        :loader="loaderWeb"
        :options="options"
        v-slot="{ items }"
      >
        <novel-list-web
          ref="novelListWebRef"
          :items="items"
          :selectable="showOperationPanel"
          :simple="!displayKeywords"
        />
      </novel-page>

      <novel-page
        v-else
        :page="page"
        :selected="selected"
        :loader="loaderWenku"
        :options="options"
        v-slot="{ items }"
      >
        <novel-list-wenku
          ref="novelListWenkuRef"
          :items="items"
          :selectable="showOperationPanel"
        />
      </novel-page>
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
