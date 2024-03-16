<script lang="ts" setup>
import { ref } from 'vue';
import { FormInst, FormItemRule, FormRules, useMessage } from 'naive-ui';
import { useRouter } from 'vue-router';

import { AuthRepository } from '@/data/api';
import { doAction } from '../util';

const router = useRouter();
const message = useMessage();

const formRef = ref<FormInst>();

const formValue = ref({
  emailOrUsername: '',
  resetPasswordToken: '',
  password: '',
  reenteredPassword: '',
});

const formRules: FormRules = {
  emailOrUsername: [
    {
      validator: (rule: FormItemRule, value: string) => value.length > 0,
      message: '邮箱/用户名不能为空',
      trigger: 'input',
    },
  ],
  resetPasswordToken: [
    {
      validator: (rule: FormItemRule, value: string) => value.length === 36,
      message: '重置密码口令应当为36位字符',
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

const resetPassword = async () => {
  try {
    await formRef.value?.validate();
  } catch (e) {
    return;
  }

  await doAction(
    AuthRepository.resetPassword(
      formValue.value.emailOrUsername,
      formValue.value.resetPasswordToken,
      formValue.value.password
    ).then(() => {
      router.push('/');
    }),
    '密码重置',
    message
  );
};

const allowSendEmail = () => true;
const sendEmail = () =>
  AuthRepository.sendResetPasswordEmail(formValue.value.emailOrUsername);
</script>

<template>
  <n-card title="重置密码">
    <n-form
      ref="formRef"
      :model="formValue"
      :rules="formRules"
      label-placement="left"
      label-width="auto"
      style="max-width: 400px"
    >
      <n-form-item-row path="emailOrUsername">
        <n-input
          v-model:value="formValue.emailOrUsername"
          placeholder="邮箱/用户名"
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>
      <div style="color: #7c7c7c">*收不到口令邮件的话，记得看垃圾箱。</div>
      <n-form-item-row path="resetPasswordToken">
        <n-input-group>
          <n-input
            v-model:value="formValue.resetPasswordToken"
            placeholder="重置密码口令"
            :input-props="{ spellcheck: false }"
          />
          <EmailButton
            label="发送口令"
            :allow-send-email="allowSendEmail"
            :send-email="sendEmail"
          />
        </n-input-group>
      </n-form-item-row>
      <n-form-item-row path="password">
        <n-input
          v-model:value="formValue.password"
          type="password"
          show-password-on="click"
          placeholder="新密码"
        />
      </n-form-item-row>
      <n-form-item-row path="reenteredPassword">
        <n-input
          v-model:value="formValue.reenteredPassword"
          type="password"
          show-password-on="click"
          placeholder="重复新密码"
        />
      </n-form-item-row>
    </n-form>
    <n-button type="primary" block @click="resetPassword()">
      重置密码
    </n-button>
  </n-card>
</template>
