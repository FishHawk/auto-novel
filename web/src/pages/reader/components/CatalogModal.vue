<script lang="ts" setup>
import { SortOutlined } from '@vicons/material';
import { Locator } from '@/data';
import { GenericNovelId } from '@/model/Common';
import { useWebNovelStore } from '@/pages/novel/WebNovelStore';
import { Ok, Result, runCatching } from '@/util/result';

const props = defineProps<{
  show: boolean;
  gnid: GenericNovelId;
  chapterId: string;
}>();

const emit = defineEmits<{
  'update:show': [boolean];
}>();

type TocItem = {
  key: number;
  titleJp: string;
  titleZh?: string;
  chapterId?: string;
  createAt?: number;
};
const tocResult = shallowRef<Result<TocItem[]>>();

const tocNumber = computed(() => {
  if (tocResult.value?.ok === true) {
    return tocResult.value.value.filter((it) => it.chapterId !== undefined)
      .length;
  }
});

watch(
  () => props.show,
  async (show) => {
    if (show && tocResult.value?.ok !== true) {
      const getWebToc = async (providerId: string, novelId: string) => {
        const store = useWebNovelStore(providerId, novelId);
        const result = await store.loadNovel();
        if (result.ok) {
          let order = 0;
          return Ok(
            result.value.toc.map((it, index) => {
              const tocItem = <TocItem>{
                ...it,
                key: index,
                order: it.chapterId ? order : undefined,
              };
              if (it.chapterId) order += 1;
              return tocItem;
            }),
          );
        } else {
          return result;
        }
      };

      const getLocalToc = async (volumeId: string) => {
        const repo = await Locator.localVolumeRepository();
        const volume = await repo.getVolume(volumeId);
        if (volume === undefined) throw Error('小说不存在');
        return volume.toc.map(
          (it, index) =>
            <TocItem>{
              titleJp: it.chapterId,
              chapterId: it.chapterId,
              key: index,
            },
        );
      };

      const gnid = props.gnid;
      if (gnid.type === 'web') {
        tocResult.value = await getWebToc(gnid.providerId, gnid.novelId);
      } else if (gnid.type === 'wenku') {
        throw '不支持文库';
      } else {
        tocResult.value = await runCatching(getLocalToc(gnid.volumeId));
      }
    }
  },
);

const currentKey = computed(() => {
  if (tocResult.value?.ok !== true) {
    return undefined;
  } else {
    return tocResult.value.value.find((it) => it.chapterId === props.chapterId)
      ?.key;
  }
});

const onTocItemClick = (chapterId: string | undefined) => {
  if (chapterId !== undefined) {
    emit('update:show', false);
  }
};

const { setting } = Locator.settingRepository();
</script>

<template>
  <c-modal
    :show="show"
    @update:show="$emit('update:show', $event)"
    style="min-height: 30vh"
  >
    <template #header>
      <div style="display: flex; align-items: baseline">
        <span>目录</span>
        <n-text
          v-if="tocNumber !== undefined"
          depth="3"
          style="font-size: 12px; margin-left: 12px"
        >
          共{{ tocNumber }}章
        </n-text>
        <div style="flex: 1" />
        <c-button
          :label="setting.tocSortReverse ? '倒序' : '正序'"
          :icon="SortOutlined"
          quaternary
          size="small"
          :round="false"
          @action="setting.tocSortReverse = !setting.tocSortReverse"
        />
      </div>
    </template>

    <c-result :result="tocResult" v-slot="{ value: toc }">
      <n-virtual-list
        v-if="gnid.type == 'web'"
        :item-size="20"
        item-resizable
        :items="setting.tocSortReverse ? toc.slice().reverse() : toc"
        :default-scroll-key="currentKey"
        :scrollbar-props="{ trigger: 'none' }"
        style="max-height: 60vh"
      >
        <template #default="{ item }">
          <div
            :key="
              item.chapterId === undefined ? `/${item.titleJp}` : item.chapterId
            "
          >
            <chapter-toc-item
              :provider-id="gnid.providerId"
              :novel-id="gnid.novelId"
              :toc-item="item"
              :last-read="chapterId"
              @click="() => onTocItemClick(item.chapterId)"
            />
          </div>
        </template>
      </n-virtual-list>
    </c-result>
  </c-modal>
</template>
