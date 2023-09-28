<template>
  <MainLayout>
    <n-h1>工具箱</n-h1>
    <n-grid :x-gap="12" :y-gap="12" cols="2">
      <n-grid-item>
        <RouterNA to="/toolbox/non-archived">
          <n-card title="Epub/Txt文件翻译"> 上传并翻译Epub/Txt小说。 </n-card>
        </RouterNA>
      </n-grid-item>
      <n-grid-item>
        <RouterNA to="/toolbox/txt">
          <n-card title="TXT工具箱">
            本地加载TXT文件。你可以统计片假名次数来制作术语表。
          </n-card>
        </RouterNA>
      </n-grid-item>
    </n-grid>
  </MainLayout>
</template>
