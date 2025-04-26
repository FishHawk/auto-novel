# 轻小说机翻机器人

[![GPL-3.0](https://img.shields.io/github/license/FishHawk/auto-novel)](https://github.com/FishHawk/auto-novel#license)
[![CI-Server](https://github.com/FishHawk/auto-novel/workflows/CI-Server/badge.svg)](https://github.com/FishHawk/auto-novel/actions/workflows/CI-Server.yml)
[![CI-Web](https://github.com/FishHawk/auto-novel/workflows/CI-Web/badge.svg)](https://github.com/FishHawk/auto-novel/actions/workflows/CI-Web.yml)

> 重建巴别塔！！

[轻小说机翻机器人](https://books.fishhawk.top/)是一个自动生成轻小说机翻并分享的网站。在这里，你可以浏览日文网络小说/文库小说，或者上传你自己的 EPUB/TXT 文件，然后生成机翻版本。

## 功能

- 浏览日本网络小说，支持的网站有：[Kakuyomu](https://kakuyomu.jp/)、[小説家になろう](https://syosetu.com/)、[Novelup](https://novelup.plus/)、[Hameln](https://syosetu.org/)、[Pixiv](https://www.pixiv.net/)、[Alphapolis](https://www.alphapolis.co.jp/)。
- 生成多种机翻，支持的翻译器有：百度、有道、OpenAI-like API（例如 DeepSeek API）、[Sakura](https://huggingface.co/SakuraLLM/Sakura-14B-Qwen2.5-v1.0-GGUF)。
- 支持术语表。
- 支持多种格式，包括日文、中文以及中日对比。
- 支持生成 EPUB 和 TXT 文件。
- 支持翻译 EPUB 和 TXT 文件。
- 支持在线阅读。

## 贡献

请参考 [CONTRIBUTING.md](https://github.com/FishHawk/auto-novel/blob/main/CONTRIBUTING.md)

<a href="https://next.ossinsight.io/widgets/official/compose-recent-top-contributors?repo_id=559577341" target="_blank" style="display: block" align="left">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://next.ossinsight.io/widgets/official/compose-recent-top-contributors/thumbnail.png?repo_id=559577341&image_size=auto&color_scheme=dark" width="280">
    <img alt="Top Contributors of ant-design/ant-design - Last 28 days" src="https://next.ossinsight.io/widgets/official/compose-recent-top-contributors/thumbnail.png?repo_id=559577341&image_size=auto&color_scheme=light" width="280">
  </picture>
</a>

## 部署

> [!WARNING]
> 注意：本项目并不是为了个人部署设计的，不保证所有功能可用和前向兼容

### Docker

```bash
> git clone https://github.com/FishHawk/auto-novel.git
> cd auto-novel
```

创建并编辑 `.env` 文件，内容如下:

```bash
DATA_PATH=./data                      # 数据的存储位置
HTTPS_PROXY=https://127.0.0.1:1234    # web 小说代理，可以为空
PIXIV_COOKIE_PHPSESSID=               # Pixiv cookies，不使用 Pixiv 可以不填
```

打开 `docker-compose.yml` 文件，酌情修改。

运行 `docker compose up [-d]` (`-d` 为后台运行)。

访问 `http://localhost` 即可。

### NixOS

NixOS 可以使用第三方的 [`flake.nix`](https://gist.github.com/kurikomoe/9dd60f9613e0b8f75c137779d223da4f)。由于用了 `devenv`，可能需要 `--impure` 启动。

```envrc
use flake . --impure
```
