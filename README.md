# 轻小说机翻机器人

[![GPL-3.0](https://img.shields.io/github/license/FishHawk/auto-novel)](https://github.com/FishHawk/auto-novel#license)
[![CI-Server](https://github.com/FishHawk/auto-novel/workflows/CI-Server/badge.svg)](https://github.com/FishHawk/auto-novel/actions/workflows/CI-Server.yml)
[![CI-Web](https://github.com/FishHawk/auto-novel/workflows/CI-Web/badge.svg)](https://github.com/FishHawk/auto-novel/actions/workflows/CI-Web.yml)

> 重建巴别塔！！

[轻小说机翻机器人](https://books.fishhawk.top/)是一个自动生成轻小说机翻并分享的网站。在这里，你可以浏览日文网络小说/文库小说，或者上传你自己的 EPUB/TXT 文件，然后生成机翻版本。

## 功能

- 浏览日本网络小说，支持的网站有：
  - [Kakuyomu](https://kakuyomu.jp/)
  - [小説家になろう](https://syosetu.com/)
  - [Novelup](https://novelup.plus/)
  - [Hameln](https://syosetu.org/)
  - [Pixiv](https://www.pixiv.net/)
  - [Alphapolis](https://www.alphapolis.co.jp/)
- 生成多种机翻，支持的翻译器有：
  - 百度
  - 有道
  - GPT/DeepSeek API
  - [Sakura](https://huggingface.co/SakuraLLM/Sakura-14B-Qwen2.5-v1.0-GGUF)
- 支持术语表。
- 支持多种格式，包括日文、中文以及中日对比。
- 支持生成 EPUB 和 TXT 文件。
- 支持翻译 EPUB 和 TXT 文件。
- 支持在线阅读。

## 贡献

欢迎提交 pull request。对于重大更改，请先打开一个 issue 来讨论你要进行的更改。

<a href="https://next.ossinsight.io/widgets/official/compose-recent-top-contributors?repo_id=559577341" target="_blank" style="display: block" align="left">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://next.ossinsight.io/widgets/official/compose-recent-top-contributors/thumbnail.png?repo_id=559577341&image_size=auto&color_scheme=dark" width="280">
    <img alt="Top Contributors of ant-design/ant-design - Last 28 days" src="https://next.ossinsight.io/widgets/official/compose-recent-top-contributors/thumbnail.png?repo_id=559577341&image_size=auto&color_scheme=light" width="280">
  </picture>
</a>

### 如何参与前端开发

网站基于 Vue3 + TypeScript + Vite + [Naive ui](https://www.naiveui.com/zh-CN)开发，你可以按照下述步骤初始化开发环境。

```shell
git clone git@github.com:FishHawk/auto-novel.git
cd web
pnpm install --frozen-lockfile  # 安装依赖
pnpm run prepare # 设置Git钩子
pnpm run dev  # 启动开发服务器
```

开发服务器将实时反映代码的变化。注意，开发服务器直接与网站后端通信，请避免在开发过程中污染网站数据库。出于安全考虑，章节翻译的上传请求将被拦截。
