<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref, watch } from 'vue';

import { ApiUser } from '@/data/api/api_user';
import { Page } from '@/data/api/common';
import { ResultState } from '@/data/result';
import { UserRole, UserOutline } from '@/data/api/api_user';

const userRole = ref<UserRole>('normal');
const userRoleOptions = [
  { value: 'normal', label: '正常用户' },
  { value: 'mantainer', label: '维护者' },
  { value: 'banned', label: '封禁用户' },
];

const message = useMessage();

const currentPage = ref(1);
const pageNumber = ref(1);
const userResult = ref<ResultState<Page<UserOutline>>>();

async function loadPage(page: number) {
  userResult.value = undefined;
  const result = await ApiUser.listUser({
    page: currentPage.value - 1,
    pageSize: 50,
    role: userRole.value,
  });
  if (currentPage.value == page) {
    userResult.value = result;
    if (result.ok) {
      pageNumber.value = result.value.pageNumber;
    }
  }
}

watch(currentPage, (page) => loadPage(page), { immediate: true });

watch(userRole, () => {
  if (currentPage.value === 1) loadPage(1);
  else currentPage.value = 1;
});
</script>

<template>
  <MainLayout>
    <n-h1>用户管理</n-h1>

    <n-p>
      <n-radio-group v-model:value="userRole" name="user-role">
        <n-space>
          <n-radio
            v-for="option in userRoleOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </n-radio>
        </n-space>
      </n-radio-group>
    </n-p>

    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
    <n-divider />

    <ResultView
      :result="userResult"
      :showEmpty="(it: Page<any>) => it.items.length === 0 "
      v-slot="{ value }"
    >
      <n-list>
        <n-list-item v-for="item in value.items">
          <n-p>{{ item.username }}-{{ item.role }}</n-p>
          <n-p>{{ item.id }}</n-p>
        </n-list-item>
      </n-list>
    </ResultView>

    <n-divider />
    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
  </MainLayout>
</template>
