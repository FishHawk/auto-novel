<template>
  <div class="layout-content">
    <n-h1>工具箱</n-h1>
    <n-grid :x-gap="12" :y-gap="12" cols="2">
      <n-grid-item>
        <RouterNA to="/toolbox/txt">
          <n-card title="TXT工具箱">
            本地加载TXT文件。你可以统计片假名次数来制作术语表。
          </n-card>
        </RouterNA>
      </n-grid-item>
    </n-grid>
  </div>
</template>
