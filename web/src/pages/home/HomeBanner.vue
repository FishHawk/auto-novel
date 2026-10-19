<script lang="ts" setup>
import { WebUtil } from '@/util/web';

const router = useRouter();

const url = ref('');
const query = (url: string) => {
  if (url.length === 0) return;
  const parseResult = WebUtil.parseUrl(url);
  if (parseResult !== undefined) {
    const { providerId, novelId } = parseResult;
    router.push({ path: `/novel/${providerId}/${novelId}` });
  } else {
    router.push({ path: '/novel', query: { query: url } });
  }
};
</script>

<template>
  <div
    class="bg-black/45 bg-[url('/files-extra/banner.v1.webp')] bg-cover bg-top bg-blend-darken"
  >
    <div
      class="layout-content max-w-200! pt-5 pb-12.5 max-[600px]:pt-2.5 max-[600px]:pb-8.75"
    >
      <h1
        class="mt-7 mb-5 text-center text-[3em] font-medium text-white drop-shadow-[0.05em_0.05em_black]"
      >
        轻小说机翻机器人
      </h1>
      <n-input-group>
        <n-input
          v-model:value="url"
          size="large"
          placeholder="输入网络小说链接直接跳转，或搜索本站缓存..."
          :input-props="{ spellcheck: false }"
          @keyup.enter="query(url)"
          class="bg-body!"
        />
        <n-button size="large" type="primary" @click="query(url)">
          搜索
        </n-button>
      </n-input-group>
    </div>
  </div>
</template>
