<script lang="ts" setup>
import { useIsDesktop } from '@/data/util';
const isDesktop = useIsDesktop(850);
</script>

<template>
  <MainLayout>
    <n-space align="center" justify="space-evenly" :wrap="!isDesktop">
      <div style="max-width: 600px">
        <n-h1>赞助</n-h1>
        <n-p>
          本站在设计之初就秉持着最低运行成本和最小重开代价的理念。
          就目前来说，除开投入开发的我的个人时间，本站的每月的运行成本约为0元。
          预计这种0成本的状态会在2024年5月结束。
        </n-p>
        <n-p>
          本站永远免费供所有人使用，但如果你想表示感谢或为（未来的）服务器费用做出贡献，你可以通过支付宝/微信支持我。
          感谢你的赞助！
        </n-p>

        <!--
        <n-space>
          <a href="https://afdian.net/a/fishhawk" target="_blank">
            <n-button size="large" color="#946ce6">
              <template #icon>
                <n-icon :size="24"> <IconAfdian /> </n-icon>
              </template>
              在爱发电支持我
            </n-button>
          </a>

          <a href="https://www.patreon.com/bePatron?u=94638526" target="_blank">
            <n-button size="large" color="#f96854">
              <template #icon>
                <n-icon> <IconPatreon /> </n-icon>
              </template>
              在Patreon支持我
            </n-button>
          </a>
        </n-space> -->
      </div>

      <n-divider
        v-if="isDesktop"
        vertical
        style="height: 220px; margin-left: 48px; margin-right: 48px"
      />
      <img
        src="https://books.fishhawk.top/files-extra/pay.jpg"
        style="max-width: 500px; width: 100%; margin-top: 30px"
      />
    </n-space>
  </MainLayout>
</template>
