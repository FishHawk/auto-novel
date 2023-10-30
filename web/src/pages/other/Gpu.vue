<script lang="ts" setup>
import { onBeforeUnmount, ref } from 'vue';
import { useMessage } from 'naive-ui';

import { ApiGpu, GpuInfo } from '@/data/api/api_gpu';
import { ResultState } from '@/data/result';
import { useUserDataStore } from '@/data/stores/user_data';

const message = useMessage();
const userData = useUserDataStore();

const gpuInfo = ref<ResultState<GpuInfo>>();

async function loadGpuInfo() {
  const result = await ApiGpu.getGpuInfo();
  gpuInfo.value = result;
}
loadGpuInfo();

async function deleteJob(id: string) {
  const result = await ApiGpu.deleteGpuJob(id);
  if (result.ok) {
    message.info('删除成功');
    if (gpuInfo.value?.ok) {
      gpuInfo.value.value.jobs = gpuInfo.value.value.jobs.filter(
        (it) => it.id !== id
      );
    }
  } else {
    message.error('删除失败：' + result.error.message);
  }
}
</script>

<template>
  <MainLayout>
    <n-h1>GPU状态</n-h1>
    <ResultView
      :result="gpuInfo"
      :showEmpty="(it: GpuInfo) => false"
      v-slot="{ value: info }"
    >
      <n-space>
        <n-card
          v-for="worker of info.workers"
          :title="worker.card"
          style="width: 400px"
        >
          {{ worker.active ? '工作中' : '未工作' }}
          <br />
          <span style="white-space: pre-wrap">
            {{ worker.description }}
          </span>
          <template v-if="worker.progress !== null">
            <br />
            成功{{ worker.progress.finished }}/{{ worker.progress.total }}
            <br />
            失败{{ worker.progress.error }}/{{ worker.progress.total }}
          </template>
        </n-card>
      </n-space>
      <n-table :bordered="false" style="margin-top: 40px">
        <thead>
          <tr>
            <th><b>任务</b></th>
            <th><b>描述</b></th>
            <th><b>状态</b></th>
            <th><b>信息</b></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="job of info.jobs">
            <td>{{ job.task }}</td>
            <td>{{ job.description }}</td>
            <td>{{ job.workerUuid !== null ? '处理中' : '排队中' }}</td>
            <td>
              于<n-time :time="job.createAt * 1000" type="relative" />由{{
                job.submitter
              }}提交
              <async-button
                v-if="userData.asAdmin"
                type="error"
                text
                @async-click="() => deleteJob(job.id)"
              >
                删除
              </async-button>
            </td>
          </tr>
        </tbody>
      </n-table>
    </ResultView>
  </MainLayout>
</template>
