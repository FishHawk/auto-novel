<script lang="ts" setup>
import { useIsDesktop } from '@/data/util';
const isDesktop = useIsDesktop(850);
</script>

<template>
  <WikiLayout>
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
  </WikiLayout>
</template>
