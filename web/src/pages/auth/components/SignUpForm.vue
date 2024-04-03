<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules } from 'naive-ui';

import { AuthRepository } from '@/data/api';
import { doAction } from '@/pages/util';
import { AuthService } from '@/domain';

const emit = defineEmits<{ (e: 'signUp'): void }>();

const message = useMessage();

const formRef = ref<FormInst>();

const formValue = ref({
  email: '',
  emailCode: '',
  username: '',
  password: '',
  reenteredPassword: '',
});

const emailRegex =
  /[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?/;

const formRules: FormRules = {
  email: [
    {
      validator: (rule: FormItemRule, value: string) => emailRegex.test(value),
      message: '邮箱不合法',
      trigger: 'input',
    },
  ],
  emailCode: [
    {
      validator: (rule: FormItemRule, value: string) => /^\d{6}$/.test(value),
      message: '邮箱验证码应当为6位数字',
      trigger: 'input',
    },
  ],
  username: [
    {
      validator: (rule: FormItemRule, value: string) =>
        value.length >= 3 && value.length <= 15,
      message: '用户名应当为3～15个字符',
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
  reenteredPassword: [
    {
      validator: (rule: FormItemRule, value: string) =>
        value === formValue.value.password,
      message: '两次密码输入不一致',
      trigger: 'input',
    },
  ],
};

const signUp = async () => {
  try {
    await formRef.value?.validate();
  } catch (e) {
    return;
  }

  await doAction(
    AuthService.signUp({
      email: formValue.value.email,
      emailCode: formValue.value.emailCode,
      username: formValue.value.username,
      password: formValue.value.password,
    }).then(() => emit('signUp')),
    '注册',
    message
  );
};

const allowSendEmail = () => {
  const email = formValue.value.email;
  const allow = emailRegex.test(email);
  if (!allow) message.error('邮箱不合法');
  return allow;
};

const sendEmail = () => AuthRepository.verifyEmail(formValue.value.email);
</script>

<template>
  <n-form
    ref="formRef"
    :model="formValue"
    :rules="formRules"
    label-placement="left"
    label-width="auto"
  >
    <n-form-item-row path="email">
      <n-input
        v-model:value="formValue.email"
        placeholder="邮箱"
        :input-props="{ spellcheck: false }"
      />
    </n-form-item-row>
    <n-text depth="3">*收不到验证邮件的话，记得看垃圾箱。</n-text>
    <n-form-item-row path="emailCode">
      <n-input-group>
        <n-input
          v-model:value="formValue.emailCode"
          placeholder="邮箱验证码"
          :input-props="{ autocomplete: 'off', spellcheck: false }"
        />
        <EmailButton
          label="发送验证码"
          :allow-send-email="allowSendEmail"
          :send-email="sendEmail"
        />
      </n-input-group>
    </n-form-item-row>
    <n-form-item-row path="username">
      <n-input
        v-model:value="formValue.username"
        placeholder="用户名"
        :input-props="{ autocomplete: 'off', spellcheck: false }"
      />
    </n-form-item-row>
    <n-form-item-row path="password">
      <n-input
        v-model:value="formValue.password"
        type="password"
        show-password-on="click"
        placeholder="密码"
        :input-props="{ autocomplete: 'off' }"
      />
    </n-form-item-row>
    <n-form-item-row path="reenteredPassword">
      <n-input
        v-model:value="formValue.reenteredPassword"
        type="password"
        show-password-on="click"
        placeholder="重复密码"
        :input-props="{ autocomplete: 'off' }"
      />
    </n-form-item-row>
  </n-form>
  <n-button type="primary" block @click="signUp">注册</n-button>
</template>
