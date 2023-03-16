<script lang="ts" setup>
import { h, ref } from 'vue';
import { MenuOption } from 'naive-ui';
import { MenuFilled } from '@vicons/material';
import { useRoute } from 'vue-router';

const path = useRoute().path;

const showLoginModal = ref(false);

const signUpFormValue = ref({
  email: '',
  emailCode: '',
  username: '',
  password: '',
  repeatPassword: '',
});

const signInFormValue = ref({
  emailOrUsername: '',
  password: '',
});

function menuOption(text: string, href: string): MenuOption {
  return { label: () => h('a', { href }, text), key: href };
}

const topMenuOptions: MenuOption[] = [
  menuOption('首页', '/'),
  menuOption('列表', '/list'),
  menuOption('编辑历史', '/patch'),
];

const collapsedMenuOptions: MenuOption[] = [
  menuOption('首页', '/'),
  {
    label: '列表',
    children: [
      menuOption('已缓存小说', '/list'),
      menuOption('成为小说家：流派', '/rank/syosetu/1'),
      menuOption('成为小说家：综合', '/rank/syosetu/2'),
      menuOption('成为小说家：异世界转移/转生', '/rank/syosetu/3'),
    ],
  },
  menuOption('编辑历史', '/patch'),
];

function getTopMenuOptionKey() {
  if (path.startsWith('/patch')) {
    return '/patch';
  } else if (path.startsWith('/list') || path.startsWith('/rank')) {
    return '/list';
  } else {
    return '/';
  }
}

function signIn() {
  console.log(signInFormValue.value);
}
function signUp() {
  console.log(signUpFormValue.value);
}
function sendEmailCode() {}
function validateEmail() {}
function validateUsername() {}
</script>

<template>
  <n-layout>
    <n-layout-header bordered>
      <div class="header">
        <n-a href="/" target="_blank">
          <n-icon size="30" style="margin-right: 8px; margin-bottom: 8px">
            <img src="/robot.svg" style="width: 100%; min-width: 100%" />
          </n-icon>
        </n-a>
        <n-popover trigger="click" :width="280" style="padding: 0">
          <template #trigger>
            <n-icon
              size="24"
              class="on-mobile"
              style="padding-inline-start: 16px"
            >
              <MenuFilled />
            </n-icon>
          </template>
          <n-menu :value="path" :options="collapsedMenuOptions" />
        </n-popover>
        <div class="on-desktop">
          <n-menu
            :value="getTopMenuOptionKey()"
            mode="horizontal"
            :options="topMenuOptions"
          />
        </div>
        <div style="flex: 1"></div>
        <div>
          <n-button
            quaternary
            style="margin-right: 4px"
            @click="showLoginModal = true"
          >
            登录/注册
          </n-button>
        </div>
      </div>
    </n-layout-header>
    <slot name="full-width" />
    <div class="container">
      <slot />
    </div>
  </n-layout>
  <n-modal v-model:show="showLoginModal">
    <n-card
      style="width: 400px"
      :bordered="false"
      size="large"
      role="dialog"
      aria-modal="true"
    >
      <n-tabs
        class="card-tabs"
        default-value="signin"
        size="large"
        animated
        style="margin: 0 -4px"
        pane-style="padding-left: 4px; padding-right: 4px; box-sizing: border-box;"
      >
        <n-tab-pane name="signin" tab="登录">
          <n-form :model="signInFormValue" label-placement="left">
            <n-form-item-row>
              <n-input
                v-model:value="signInFormValue.emailOrUsername"
                placeholder="用户名/邮箱"
              />
            </n-form-item-row>
            <n-form-item-row>
              <n-input
                v-model:value="signInFormValue.password"
                type="password"
                show-password-on="click"
                placeholder="密码"
              />
            </n-form-item-row>
          </n-form>
          <n-space>
            <n-a href="/reset-password" target="_blank">忘记密码</n-a>
          </n-space>
          <n-button
            type="primary"
            block
            style="margin-top: 20px"
            @click="signIn()"
          >
            登录
          </n-button>
        </n-tab-pane>

        <n-tab-pane name="signup" tab="注册">
          <n-form :model="signUpFormValue" label-placement="left">
            <n-form-item-row>
              <n-input
                v-model:value="signUpFormValue.email"
                placeholder="邮箱"
              />
            </n-form-item-row>
            <n-form-item-row>
              <n-input-group>
                <n-input
                  v-model:value="signUpFormValue.emailCode"
                  placeholder="邮箱验证码"
                />
                <n-button type="primary"> 发送验证码 </n-button>
              </n-input-group>
            </n-form-item-row>
            <n-form-item-row>
              <n-input
                v-model:value="signUpFormValue.username"
                placeholder="用户名"
              />
            </n-form-item-row>
            <n-form-item-row>
              <n-input
                v-model:value="signUpFormValue.password"
                type="password"
                show-password-on="click"
                placeholder="密码"
              />
            </n-form-item-row>
            <n-form-item-row>
              <n-input
                v-model:value="signUpFormValue.repeatPassword"
                type="password"
                show-password-on="click"
                placeholder="重复密码"
              />
            </n-form-item-row>
          </n-form>
          <n-button type="primary" block @click="signUp()"> 注册 </n-button>
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </n-modal>
</template>

<style>
.header {
  margin: auto;
  display: flex;
  align-items: center;
  height: 50px;
}
@media only screen and (min-width: 600px) {
  .header,
  .container {
    width: 1000px;
    padding-left: 30px;
    padding-right: 30px;
  }
  .container {
    margin: 0 auto 48px;
  }
}
@media only screen and (max-width: 600px) {
  .header,
  .container {
    width: auto;
  }
  .container {
    margin: 0 12px 48px;
  }
}
</style>
