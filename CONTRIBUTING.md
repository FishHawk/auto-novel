# 贡献代码

感谢您有兴趣为这个项目做出贡献！为了高效协作，请遵循以下规范。

- 在编写代码前，请先通过 Issue 或群组讨论你的变更计划，确保与现有开发方向一致。
- 提交 Pull Request 时，请保持内容精简，每次聚焦一个独立的修改点，以便快速检视和合入。
- 如果对当前代码设计有疑问，可以在群组里@FishHawk提问。
- 如果使用 AI 辅助编写，请务必自己检视一遍。

## 如何参与前端开发

网站基于 Vue3 + TypeScript + Vite + [Naive ui](https://www.naiveui.com/zh-CN) 开发。

首先准备开发环境：

```bash
git clone git@github.com:FishHawk/auto-novel.git
cd web
pnpm install --frozen-lockfile # 安装依赖
pnpm prepare                   # 设置Git钩子
```

然后根据你的需要，选择合适的方式启动开发服务器：

```bash
pnpm dev        # 启动开发服务器，连接到机翻站后端服务器
pnpm dev:local  # 启动开发服务器，连接到本地启动的后端服务器，http://localhost:8081
pnpm dev --host # 启动开发服务器，暴露到局域网，可以使用手机访问调试
```

注意，如果开发服务器连接到机翻站后端，请避免在开发过程中污染网站数据库。出于安全考虑，开发环境不会上传章节翻译。

## 如何参与后端开发

后端基于 JVM17 + Kotlin + Ktor 开发，推荐使用 IntelliJ IDEA 打开项目。

如果你的修改涉及数据库，你需要自己[部署数据库](https://github.com/FishHawk/auto-novel/blob/main/README.md#部署)并设置环境变量：

```bash
DB_HOST_TEST=1127.0.0.1 # 数据库 IP 地址
```

如果你的修改不涉及 Http API，可以使用 kotest 调试，推荐安装 kotest 插件。

如果你的修改设计 Http API,你可以使用 `pnpm dev:local` 启动开发服务器，参考上一节。
