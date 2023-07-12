<script lang="ts" setup>
import { ref } from 'vue';

import { Err, Ok, ResultState } from '@/data/api/result';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const authInfoStore = useAuthInfoStore();

const userNovelId = ref('');
const volumesResult = ref<ResultState<VolumeJpDto[]>>();
const volumesUserResult = ref<ResultState<VolumeJpDto[]>>();

async function getVolumesNonArchived() {
  const result = await ApiWenkuNovel.listVolumesNonArchived();
  volumesResult.value = result;
}
getVolumesNonArchived();

async function getVolumesUser() {
  const token = authInfoStore.token;
  if (token) {
    const result = await ApiWenkuNovel.listVolumesUser(token);
    if (result.ok) {
      volumesUserResult.value = Ok(result.value.list);
      userNovelId.value = result.value.novelId;
    } else {
      volumesUserResult.value = Err(result.error);
    }
  } else {
    volumesUserResult.value = Ok([]);
  }
}
getVolumesUser();
</script>

<template>
  <MainLayout>
    <n-h1>Epub/Txt翻译</n-h1>
    <n-p>
      上传日文Epub/Txt小说，可以像翻译网络小说一样生成中文版。如何使用翻译插件请参考
      <n-a href="/how-to-use" target="_blank">使用说明</n-a>。
    </n-p>
    <n-p> 本功能还在测试中，请注意： </n-p>
    <n-ul>
      <n-li>是Epub还是Txt是用后缀名区分的。</n-li>
      <n-li>
        Epub的格式千奇百怪，如果生成的中文版有问题，请向我
        <n-a href="/feedback" target="_blank">反馈</n-a>。
      </n-li>
      <n-li>
        <b>现在生成的Epub很多是损坏的</b>
        ，可以用Sigil打开再保存来修复（别的类似的打开再保存的方法也行）。具体原因有待测试。
      </n-li>
    </n-ul>

    <SectionHeader title="私人缓存区" />
    <n-p>
      这里上传的小说是不公开的，如果你的小说有放流时间等限制无法公开，可以上传到这里。
      <br />
      上传到这里的小说过一段时间会清理（现在还没做）。
    </n-p>
    <ListVolumes
      type="jp"
      :volumesResult="volumesUserResult"
      :novelId="userNovelId"
      @uploadFinished="getVolumesUser()"
    />

    <SectionHeader title="通用缓存区" />
    <n-p>
      这里上传的小说是公开的，在上传之前请确定这个网站还没有你的小说页面。如果有的话，请在小说页面上传。
      <br />
      上传到这里的小说过一段时间会被我移动到对应的文库版页面里面。
    </n-p>
    <ListVolumes
      type="jp"
      :volumesResult="volumesResult"
      novelId="non-archived"
      @uploadFinished="getVolumesNonArchived()"
    />
  </MainLayout>
</template>
