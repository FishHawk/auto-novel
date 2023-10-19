<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules, useMessage } from 'naive-ui';
import { ref } from 'vue';

import { ApiAuth, SignInDto } from '@/data/api/api_auth';

const emits = defineEmits<{ (e: 'signIn', user: SignInDto): void }>();

const message = useMessage();

const formRef = ref<FormInst | null>(null);

const formValue = ref({
  emailOrUsername: '',
  password: '',
});

const rules: FormRules = {
  emailOrUsername: [
    {
      validator: (rule: FormItemRule, value: string) => value.length > 0,
      message: '邮箱/用户名不能为空',
      trigger: 'input',
    },
  ],
  password: [
    {
      validator: (rule: FormItemRule, value: string) => value.length >= 8,
      message: '密码至少为8个字符',
      trigger: 'input',
    },
  ],
};

function signIn() {
  formRef.value?.validate(async (errors) => {
    if (errors) return;

    const userResult = await ApiAuth.signIn(
      formValue.value.emailOrUsername,
      formValue.value.password
    );

    if (userResult.ok) {
      emits('signIn', userResult.value);
    } else {
      message.error('登录失败:' + userResult.error.message);
    }
  });
}
</script>

<template>
  <n-form
    ref="formRef"
    :model="formValue"
    :rules="rules"
    label-placement="left"
  >
    <n-form-item-row path="emailOrUsername">
      <n-input
        v-model:value="formValue.emailOrUsername"
        placeholder="用户名/邮箱"
        :input-props="{ spellcheck: false }"
      />
    </n-form-item-row>
    <n-form-item-row path="password">
      <n-input
        v-model:value="formValue.password"
        type="password"
        show-password-on="click"
        placeholder="密码"
        @keyup.enter="signIn()"
      />
    </n-form-item-row>
  </n-form>
  <n-space>
    <RouterNA to="/reset-password">忘记密码</RouterNA>
  </n-space>
  <n-button type="primary" block style="margin-top: 20px" @click="signIn()">
    登录
  </n-button>
</template>
