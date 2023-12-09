<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules, useMessage } from 'naive-ui';
import { ref } from 'vue';

import { ApiAuth, SignInDto } from '@/data/api/api_auth';

const emit = defineEmits<{ (e: 'signIn', user: SignInDto): void }>();

const message = useMessage();

const formRef = ref<FormInst>();

const formValue = ref({
  emailOrUsername: '',
  password: '',
});

const formRules: FormRules = {
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

const signIn = async () => {
  try {
    await formRef.value?.validate();
  } catch (e) {
    return;
  }

  const result = await ApiAuth.signIn(
    formValue.value.emailOrUsername,
    formValue.value.password
  );

  if (result.ok) {
    emit('signIn', result.value);
  } else {
    message.error('登录失败:' + result.error.message);
  }
};
</script>

<template>
  <n-form
    ref="formRef"
    :model="formValue"
    :rules="formRules"
    label-placement="left"
    label-width="auto"
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

  <RouterNA to="/reset-password">忘记密码</RouterNA>
  <n-button type="primary" block @click="signIn" style="margin-top: 20px">
    登录
  </n-button>
</template>
