<script lang="ts" setup>
import { CloseFilled, DoneFilled } from '@vicons/material';
import { createReusableTemplate } from '@vueuse/core';
import { useMessage, useThemeVars } from 'naive-ui';
import { ref } from 'vue';

import {
  ApiSakura,
  SakuraJob,
  SakuraStatus,
  SakuraWorker,
} from '@/data/api/api_sakura';
import { ResultState } from '@/data/result';
import { useUserDataStore } from '@/data/stores/user_data';

const [DefineJob, ReuseJob] = createReusableTemplate<{
  job: SakuraJob;
  worker?: SakuraWorker;
}>();

const vars = useThemeVars();
const message = useMessage();
const userData = useUserDataStore();

const sakuraStatus = ref<ResultState<SakuraStatus>>();

async function loadSakuraInfo() {
  const result = await ApiSakura.getSakuraStatus();
  sakuraStatus.value = result;
}
loadSakuraInfo();

async function deleteJob(id: string) {
  const result = await ApiSakura.deleteSakuraJob(id);
  if (result.ok) {
    message.info('删除成功');
    if (sakuraStatus.value?.ok) {
      sakuraStatus.value.value.jobs = sakuraStatus.value.value.jobs.filter(
        (it) => it.id !== id
      );
    }
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

async function createSakuraWorker(json: { gpu: string; endpoint: string }) {
  const result = await ApiSakura.createSakuraWorker(json);
  if (result.ok) {
    message.info('创建成功');
    showCreateWorkerModal.value = false;
    loadSakuraInfo();
  } else {
    message.error('创建失败：' + result.error.message);
  }
}

async function deleteSakuraWorker(id: string) {
  const result = await ApiSakura.deleteSakuraWorker(id);
  if (result.ok) {
    message.info('删除成功');
    loadSakuraInfo();
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

async function startSakuraWorker(id: string) {
  const result = await ApiSakura.startSakuraWorker(id);
  if (result.ok) {
    message.info('启动成功');
    loadSakuraInfo();
  } else {
    message.error('启动失败：' + result.error.message);
  }
}

async function stopSakuraWorker(id: string) {
  const result = await ApiSakura.stopSakuraWorker(id);
  if (result.ok) {
    message.info('暂停成功');
    loadSakuraInfo();
  } else {
    message.error('暂停失败：' + result.error.message);
  }
}

const showInactiveWorker = ref(false);
const onlyShowWorkerAddedByMe = ref(false);
function filterAndSortWorker(workers: SakuraWorker[]) {
  return workers
    .filter(
      (it: SakuraWorker) =>
        (it.active || showInactiveWorker.value) &&
        (it.username === userData.username || !onlyShowWorkerAddedByMe.value)
    )
    .sort((a: SakuraWorker, b: SakuraWorker) =>
      a.username.localeCompare(b.username)
    );
}

function computePercentage({
  total,
  finished,
}: {
  total: number;
  finished: number;
}) {
  if (total == 0) {
    return 100;
  } else {
    return Math.round((1000 * finished) / total) / 10;
  }
}

const showCreateWorkerModal = ref(false);
const createWorkerFormValue = ref({
  gpu: '',
  endpoint: '',
});
</script>

<template>
  <DefineJob v-slot="{ job, worker }">
    <tr>
      <td>
        <n-text depth="3" style="font-size: 12px">{{ job.task }}</n-text>
        <br />
        {{ job.description }}
        <template v-if="worker?.progress">
          <br />
          <n-progress :percentage="computePercentage(worker.progress)" />
        </template>
      </td>
      <td style="white-space: nowrap">
        {{ job.workerId !== null ? '处理中' : '排队中' }}
        <template v-if="worker?.progress">
          <br />
          {{ worker.progress.finished }}/{{ worker.progress.total }}
        </template>
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
  </DefineJob>

  <MainLayout>
    <n-h1>Sakura状态</n-h1>
    <ResultView
      :result="sakuraStatus"
      :showEmpty="(it: SakuraStatus) => false"
      v-slot="{ value: info }"
    >
      <n-space
        item-style="display: flex;"
        align="center"
        style="margin-bottom: 24px"
      >
        <n-checkbox v-model:checked="showInactiveWorker">
          显示未激活的 Worker
        </n-checkbox>
        <n-checkbox
          v-if="userData.isMaintainer"
          v-model:checked="onlyShowWorkerAddedByMe"
        >
          只显示我添加的 Worker
        </n-checkbox>
        <n-button
          v-if="userData.isMaintainer"
          @click="showCreateWorkerModal = true"
        >
          添加 Worker
        </n-button>
      </n-space>

      <n-list>
        <n-list-item
          v-for="worker of filterAndSortWorker(info.workers)"
          :key="worker.id"
        >
          <n-thing content-indented>
            <template #avatar>
              <n-icon-wrapper
                :size="20"
                :border-radius="10"
                :color="worker.active ? undefined : vars.errorColor"
              >
                <n-icon
                  :size="16"
                  :component="worker.active ? DoneFilled : CloseFilled"
                />
              </n-icon-wrapper>
            </template>

            <template #header>
              <n-space>
                <n-popover v-if="worker.endpoint" trigger="hover">
                  <template #trigger>
                    {{ worker.gpu }}
                  </template>
                  <span>{{ worker.endpoint }}</span>
                </n-popover>
                <template v-else>
                  {{ worker.gpu }}
                </template>

                <n-text depth="3" style="font-size: 14px">
                  @{{ worker.username }}
                </n-text>
              </n-space>
            </template>

            <template #header-extra>
              <n-space
                v-if="userData.asAdmin || userData.username === worker.username"
                :wrap="false"
              >
                <async-button
                  v-if="worker.active"
                  size="small"
                  secondary
                  @async-click="() => stopSakuraWorker(worker.id)"
                >
                  暂停
                </async-button>
                <async-button
                  v-else
                  size="small"
                  secondary
                  @async-click="() => startSakuraWorker(worker.id)"
                >
                  启动
                </async-button>
                <async-button
                  size="small"
                  secondary
                  type="error"
                  @async-click="() => deleteSakuraWorker(worker.id)"
                >
                  删除
                </async-button>
              </n-space>
              <template v-else>
                {{ worker.active ? '工作中' : '已暂停' }}
              </template>
            </template>

            <template #description>
              <n-text
                v-if="worker.active || userData.asAdmin"
                style="white-space: pre-wrap"
              >
                {{ worker.description }}
              </n-text>
            </template>
          </n-thing>
        </n-list-item>
      </n-list>
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
            @click="createSakuraWorker(createWorkerFormValue)"
          >
            添加
          </n-button>
        </n-card>
      </n-modal>

      <SectionHeader :title="`任务队列 [${info.jobs.length}/150]`" />
      <n-table :bordered="false" style="margin-top: 40px">
        <thead>
          <tr>
            <th><b>描述</b></th>
            <th><b>状态</b></th>
            <th><b>信息</b></th>
          </tr>
        </thead>
        <tbody>
          <ReuseJob
            v-for="job of info.jobs"
            :key="job.id"
            :job="job"
            :worker="info.workers.find((worker:SakuraWorker) => worker.id === job.workerId)"
          />
        </tbody>
      </n-table>
    </ResultView>
  </MainLayout>
</template>
