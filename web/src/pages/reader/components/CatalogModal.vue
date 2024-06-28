<script lang="ts" setup>
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
  nav: [chapterId: string];
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
          return Ok(
            result.value.toc.map(
              (it, index) =>
                <TocItem>{
                  ...it,
                  key: index,
                },
            ),
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
    emit('nav', chapterId);
    emit('update:show', false);
  }
};
</script>

<template>
  <c-modal
    :show="show"
    @update:show="$emit('update:show', $event)"
    style="min-height: 30vh"
  >
    <template #header>
      目录
      <n-text
        v-if="tocNumber !== undefined"
        depth="3"
        style="font-size: 12px; margin-left: 12px"
      >
        共{{ tocNumber }}章
      </n-text>
    </template>

    <c-result :result="tocResult" v-slot="{ value: toc }">
      <n-virtual-list
        :item-size="20"
        item-resizable
        :items="toc"
        :default-scroll-key="currentKey"
        :scrollbar-props="{ trigger: 'none' }"
        style="max-height: 60vh"
      >
        <template #default="{ item }">
          <div
            :key="item.index"
            style="width: 100%"
            @click="() => onTocItemClick(item.chapterId)"
          >
            <div style="padding-top: 12px">
              <n-text
                :type="
                  item.key === currentKey
                    ? 'warning'
                    : item.chapterId
                      ? 'success'
                      : 'default'
                "
              >
                {{ item.titleJp }}
              </n-text>
              <br />
              <n-text depth="3">
                {{ item.titleZh }}
              </n-text>
            </div>
          </div>
        </template>
      </n-virtual-list>
    </c-result>
  </c-modal>
</template>
