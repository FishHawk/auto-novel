<script lang="ts" setup>
import { UserRepository } from '@/data/api';
import { Result, runCatching } from '@/util/result';
import { Page } from '@/model/Page';
import { UserOutline, UserRole } from '@/model/User';

const userRole = ref<UserRole>('normal');
const userRoleOptions = [
  { value: 'normal', label: '正常用户' },
  { value: 'mantainer', label: '维护者' },
  { value: 'banned', label: '封禁用户' },
];

const currentPage = ref(1);
const pageNumber = ref(1);
const userResult = ref<Result<Page<UserOutline>>>();

async function loadPage(page: number) {
  userResult.value = undefined;
  const result = await runCatching(
    UserRepository.listUser({
      page: currentPage.value - 1,
      pageSize: 50,
      role: userRole.value,
    }),
  );
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

const roleToReadableText = (role: UserRole) => {
  if (role === 'normal') return '普通用户';
  else if (role === 'trusted') return '信任用户';
  else if (role === 'maintainer') return '维护者';
  else if (role === 'admin') return '管理员';
  else if (role === 'banned') return '封禁用户';
  else return '未知';
};
</script>

<template>
  <n-p>
    <c-radio-group v-model:value="userRole" :options="userRoleOptions" />
  </n-p>

  <n-pagination
    v-if="pageNumber > 1"
    v-model:page="currentPage"
    :page-count="pageNumber"
    :page-slot="7"
  />
  <n-divider />

  <c-result
    :result="userResult"
    :show-empty="(it: Page<any>) => it.items.length === 0"
    v-slot="{ value }"
  >
    <n-table :bordered="false">
      <thead>
        <tr>
          <th><b>用户名</b></th>
          <th><b>邮箱</b></th>
          <th><b>操作</b></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in value.items" :key="user.id">
          <td>{{ roleToReadableText(user.role) }}-{{ user.username }}</td>
          <td>{{ user.email }}</td>
          <td></td>
        </tr>
      </tbody>
    </n-table>
  </c-result>

  <n-divider />
  <n-pagination
    v-if="pageNumber > 1"
    v-model:page="currentPage"
    :page-count="pageNumber"
    :page-slot="7"
  />
</template>
