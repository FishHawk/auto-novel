<script lang="ts" setup>
import { computed, onBeforeUnmount, ref } from 'vue';
import { FormInst, FormItemRule, FormRules, useMessage } from 'naive-ui';

import ApiAuth, { SignInDto } from '@/data/api/api_auth';

const emits = defineEmits<{ (e: 'signUp', user: SignInDto): void }>();

const message = useMessage();

const formRef = ref<FormInst | null>(null);

const formValue = ref({
  email: '',
  emailCode: '',
  username: '',
  password: '',
  reenteredPassword: '',
});

const emailRegex =
  /[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?/;

const rules: FormRules = {
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

function signUp() {
  formRef.value?.validate(async (errors) => {
    if (errors) return;

    const userResult = await ApiAuth.signUp(
      formValue.value.email,
      formValue.value.emailCode,
      formValue.value.username,
      formValue.value.password
    );

    if (userResult.ok) {
      emits('signUp', userResult.value);
    } else {
      message.error('注册失败:' + userResult.error.message);
    }
  });
}

type VerifyState =
  | { state: 'sending' }
  | { state: 'cooldown'; seconds: number }
  | undefined;

const verifyState = ref<VerifyState>(undefined);

async function verifyEmail() {
  if (verifyState.value !== undefined) return;

  const email = formValue.value.email;
  if (!emailRegex.test(email)) {
    message.error('邮箱不合法');
    return;
  }

  verifyState.value = { state: 'sending' };
  const result = await ApiAuth.verifyEmail(email);

  if (result.ok) {
    verifyState.value = { state: 'cooldown', seconds: 60 };
    message.info(result.value);

    const timer = window.setInterval(() => {
      if (
        verifyState.value?.state === 'cooldown' &&
        verifyState.value.seconds > 0
      ) {
        verifyState.value.seconds--;
      } else {
        verifyState.value = undefined;
        window.clearInterval(timer);
      }
    }, 1000);
  } else {
    verifyState.value = undefined;
    message.error('发送验证码失败:' + result.error.message);
  }
}
const verifyButtonLabel = computed(() => {
  if (verifyState.value === undefined) {
    return '发送验证码';
  } else if (verifyState.value.state === 'sending') {
    return '发送中';
  } else {
    return `${verifyState.value.seconds}秒冷却`;
  }
});
</script>

<template>
  <n-form
    ref="formRef"
    :model="formValue"
    :rules="rules"
    label-placement="left"
  >
    <n-form-item-row path="email">
      <n-input v-model:value="formValue.email" placeholder="邮箱" />
    </n-form-item-row>
    <div style="color: #7c7c7c">*收不到验证邮件的话，记得看垃圾箱。</div>
    <n-form-item-row path="emailCode">
      <n-input-group>
        <n-input v-model:value="formValue.emailCode" placeholder="邮箱验证码" />
        <n-button
          type="primary"
          :disabled="verifyState"
          @click="verifyEmail()"
          style="width: 100px"
        >
          {{ verifyButtonLabel }}
        </n-button>
      </n-input-group>
    </n-form-item-row>
    <n-form-item-row path="username">
      <n-input v-model:value="formValue.username" placeholder="用户名" />
    </n-form-item-row>
    <n-form-item-row path="password">
      <n-input
        v-model:value="formValue.password"
        type="password"
        show-password-on="click"
        placeholder="密码"
      />
    </n-form-item-row>
    <n-form-item-row path="reenteredPassword">
      <n-input
        v-model:value="formValue.reenteredPassword"
        type="password"
        show-password-on="click"
        placeholder="重复密码"
      />
    </n-form-item-row>
  </n-form>
  <n-button type="primary" block @click="signUp()"> 注册 </n-button>
</template>
