<script lang="ts" setup>
import { createReusableTemplate } from '@vueuse/core';
import { useMessage } from 'naive-ui';
import { getScrollParent } from 'seemly';
import { computed, ref, shallowRef, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ApiUser } from '@/data/api/api_user';
import { ApiWebNovel, WebNovelChapterDto } from '@/data/api/api_web_novel';
import { Ok, ResultState } from '@/data/result';
import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { useUserDataStore } from '@/data/stores/user_data';
import { useIsDesktop } from '@/data/util';
import { buildWebChapterUrl } from '@/data/util_web';

const [DefineChapterLink, ReuseChapterLink] = createReusableTemplate<{
  id: string | undefined;
}>();

const isDesktop = useIsDesktop(800);
const userData = useUserDataStore();
const setting = useReaderSettingStore();
const route = useRoute();
const message = useMessage();

const providerId = route.params.providerId as string;
const novelId = route.params.novelId as string;


const currentChapterId = ref(route.params.chapterId as string);
const chapters = new Map<string, WebNovelChapterDto>();
const chapterResult = shallowRef<ResultState<WebNovelChapterDto>>();

const loadChapter = async (chapterId: string) => {
  const chapterStored = chapters.get(chapterId);
  if (chapterStored === undefined) {
    const result = await ApiWebNovel.getChapter(providerId, novelId, chapterId);
    if (result.ok) {
      chapters.set(chapterId, result.value);
    }
    return result;
  } else {
    return Ok(chapterStored);
  }
};

const navToChapter = async (targetChapterId: string) => {
  currentChapterId.value = targetChapterId;
};

const placeholderRef = ref<HTMLElement>();
watch(
  currentChapterId,
  async (chapterId, oldChapterId) => {
    const result = await loadChapter(chapterId);
    if (placeholderRef.value) {
      getScrollParent(placeholderRef.value)?.scrollTo({
        top: 0,
        behavior: 'instant',
      });
    }

    if (oldChapterId !== chapterId) {
      window.history.pushState(
        {},
        document.title,
        `/novel/${providerId}/${novelId}/${chapterId}`
      );
    }
    if (result.ok) {
      document.title = result.value.titleJp;
      chapterResult.value = result;
      if (userData.isLoggedIn) {
        ApiUser.updateReadHistoryWeb(providerId, novelId, chapterId);
      }
      if (chapters.size > 1 && result.value.nextId) {
        loadChapter(result.value.nextId);
      }
    }
  },
  { immediate: true }
);

const isMobile = (() => {
  const a = navigator.userAgent || navigator.vendor || (window as any).opera;
  if (
    /(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino/i.test(
      a
    ) ||
    /1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\-(n|u)|c55\/|capi|ccwa|cdm\-|cell|chtm|cldc|cmd\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\-s|devi|dica|dmob|do(c|p)o|ds(12|\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\-|_)|g1 u|g560|gene|gf\-5|g\-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd\-(m|p|t)|hei\-|hi(pt|ta)|hp( i|ip)|hs\-c|ht(c(\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\-(20|go|ma)|i230|iac( |\-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc\-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|\-[a-w])|libw|lynx|m1\-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\-2|po(ck|rt|se)|prox|psio|pt\-g|qa\-a|qc(07|12|21|32|60|\-[2-7]|i\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\-|oo|p\-)|sdk\/|se(c(\-|0|1)|47|mc|nd|ri)|sgh\-|shar|sie(\-|m)|sk\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\-|v\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\-|tdg\-|tel(i|m)|tim\-|t\-mo|to(pl|sh)|ts(70|m\-|m3|m5)|tx\-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\-|your|zeto|zte\-/i.test(
      a.substr(0, 4)
    )
  ) {
    return true;
  }
  return false;
})();

const url = computed(() =>
  buildWebChapterUrl(providerId, novelId, currentChapterId.value)
);
</script>

<template>
  <DefineChapterLink v-slot="{ $slots, id: chapterId }">
    <n-button
      :disabled="!chapterId"
      quaternary
      :type="chapterId ? 'primary' : 'default'"
      @click="() => navToChapter(chapterId!!)"
    >
      <component :is="$slots.default!" />
    </n-button>
  </DefineChapterLink>

  <div ref="placeholderRef" class="content">
    <ResultView
      :result="chapterResult"
      :showEmpty="() => false"
      v-slot="{ value: chapter }"
    >
      <n-flex
        align="center"
        justify="space-between"
        :wrap="false"
        style="width: 100%; margin-top: 20px"
      >
        <ReuseChapterLink :id="chapter.prevId">上一章</ReuseChapterLink>
        <n-h4 style="text-align: center; margin: 0">
          <n-a :href="url">{{ chapter.titleJp }}</n-a>
          <br />
          <n-text depth="3">{{ chapter.titleZh }}</n-text>
        </n-h4>
        <ReuseChapterLink :id="chapter.nextId">下一章</ReuseChapterLink>
      </n-flex>

      <n-divider />

      <web-reader-layout-mobile
        v-if="isMobile"
        :provider-id="providerId"
        :novel-id="novelId"
        :chapter-id="currentChapterId"
        :chapter="chapter"
        @nav="navToChapter"
      >
        <web-reader-content
          :provider-id="providerId"
          :novel-id="novelId"
          :chapter-id="currentChapterId"
          :chapter="chapter"
        />
      </web-reader-layout-mobile>
      <web-reader-layout-desktop
        v-else
        :provider-id="providerId"
        :novel-id="novelId"
        :chapter-id="currentChapterId"
        :chapter="chapter"
        @nav="navToChapter"
      >
        <web-reader-content
          :provider-id="providerId"
          :novel-id="novelId"
          :chapter-id="currentChapterId"
          :chapter="chapter"
        />
      </web-reader-layout-desktop>

      <n-divider />

      <n-flex align="center" justify="space-between" style="width: 100%">
        <ReuseChapterLink :id="chapter.prevId">上一章</ReuseChapterLink>
        <ReuseChapterLink :id="chapter.nextId">下一章</ReuseChapterLink>
      </n-flex>
    </ResultView>
  </div>
</template>

<style scoped>
.content {
  max-width: 800px;
  margin: 0 auto;
  padding-left: 24px;
  padding-right: 24px;
  padding-bottom: 48px;
}
@media only screen and (max-width: 600px) {
  .content {
    padding-left: 12px;
    padding-right: 12px;
  }
}
</style>
