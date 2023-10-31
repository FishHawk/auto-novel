<script lang="ts" setup>
import { ref } from 'vue';
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

async function createGpuWorker(json: { gpu: string; endpoint: string }) {
  const result = await ApiGpu.createGpuWorker(json);
  if (result.ok) {
    message.info('创建成功');
    showCreateWorkerModal.value = false;
    loadGpuInfo();
  } else {
    message.error('创建失败：' + result.error.message);
  }
}

async function deleteGpuWorker(id: string) {
  const result = await ApiGpu.deleteGpuWorker(id);
  if (result.ok) {
    message.info('删除成功');
    loadGpuInfo();
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

async function startGpuWorker(id: string) {
  const result = await ApiGpu.startGpuWorker(id);
  if (result.ok) {
    message.info('启动成功');
    loadGpuInfo();
  } else {
    message.error('启动失败：' + result.error.message);
  }
}

async function stopGpuWorker(id: string) {
  const result = await ApiGpu.stopGpuWorker(id);
  if (result.ok) {
    message.info('暂停成功');
    loadGpuInfo();
  } else {
    message.error('暂停失败：' + result.error.message);
  }
}

function computePercentage({
  total,
  finished,
  error,
}: {
  total: number;
  finished: number;
  error: number;
}) {
  if (total == 0) {
    return 100;
  } else {
    return Math.round((1000 * (finished + error)) / total) / 10;
  }
}

const showCreateWorkerModal = ref(false);
const createWorkerFormValue = ref({
  gpu: '',
  endpoint: '',
});
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
          :title="worker.gpu"
          style="width: 400px"
        >
          <template #header-extra>
            {{ worker.active ? '工作中' : '已暂停' }}
          </template>
          <template #action v-if="userData.asAdmin">
            <n-space>
              <async-button
                v-if="worker.active"
                secondary
                @async-click="() => stopGpuWorker(worker.id)"
              >
                暂停
              </async-button>
              <async-button
                v-else
                secondary
                @async-click="() => startGpuWorker(worker.id)"
              >
                启动
              </async-button>
              <async-button
                secondary
                type="error"
                @async-click="() => deleteGpuWorker(worker.id)"
              >
                删除
              </async-button>
            </n-space>
          </template>

          <div style="display: flex">
            <div style="flex: auto; margin-right: 20px">
              <span>{{ worker.id }}</span>
              <br />
              <span style="white-space: pre-wrap">
                {{ worker.description }}
              </span>
            </div>
            <n-space
              v-if="worker.progress !== null"
              align="center"
              vertical
              size="large"
              style="flex: none"
            >
              <n-progress
                type="circle"
                :percentage="computePercentage(worker.progress)"
              />
              <n-text>
                成功 {{ worker.progress.finished }}/{{ worker.progress.total }}
                <br />
                失败 {{ worker.progress.error }}/{{ worker.progress.total }}
              </n-text>
            </n-space>
          </div>
        </n-card>
      </n-space>

      <n-button
        v-if="userData.asAdmin"
        @click="showCreateWorkerModal = true"
        style="margin-top: 30px"
      >
        添加GPU
      </n-button>
      <n-modal v-model:show="showCreateWorkerModal">
        <n-card
          style="width: min(400px, calc(100% - 16px))"
          :bordered="false"
          size="large"
          role="dialog"
          aria-modal="true"
        >
          <n-form
            ref="formRef"
            :model="createWorkerFormValue"
            label-placement="left"
          >
            <n-form-item-row path="gpu">
              <n-input
                v-model:value="createWorkerFormValue.gpu"
                placeholder="GPU"
                :input-props="{ spellcheck: false }"
              />
            </n-form-item-row>
            <n-form-item-row path="endpoint">
              <n-input
                v-model:value="createWorkerFormValue.endpoint"
                placeholder="Endpoint"
                :input-props="{ spellcheck: false }"
              />
            </n-form-item-row>
          </n-form>
          <n-button
            type="primary"
            block
            style="margin-top: 20px"
            @click="createGpuWorker(createWorkerFormValue)"
          >
            添加
          </n-button>
        </n-card>
      </n-modal>

      <SectionHeader :title="`任务队列 [${info.jobs.length}/30]`" />
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
            <td style="max-width: 300px">{{ job.task }}</td>
            <td>{{ job.description }}</td>
            <td style="white-space: nowrap">
              {{ job.workerUuid !== null ? '处理中' : '排队中' }}
            </td>
            <td style="white-space: nowrap">
              于<n-time :time="job.createAt * 1000" type="relative" />
              <br />
              由{{ job.submitter }}提交
              <async-button
                v-if="userData.asAdmin || userData.username === job.submitter"
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
