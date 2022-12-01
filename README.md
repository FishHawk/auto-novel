# Web Novel Ebook Generator

[网络小说 epub/txt 生成器](https://books.fishhawk.top/)的项目代码，可以将日本网络小说翻译成中文并转换成电子书。可以通过安装插件启动本地加速。

支持的站点：

- [KAKUYOMU/カクヨム](https://kakuyomu.jp/)
- [成为小说家/小説家になろう](https://syosetu.com/)
- [ノベルアップ＋](https://novelup.plus/)
- [HAMELN](https://syosetu.org/)

支持的格式：

- TXT，原文对比版 TXT
- EPUB，原文对比版 EPUB

支持的翻译器：

- 百度网页版
- 百度开发者版


## 本地运行

1. 安装依赖：`pip install -r requirements.txt`。
2. 下载小说：`python backend/script/cli.py "小说的url" --epub --txt --zh`。这会下载小说并翻译成中文，同时生成 EPUB 和 TXT 两种格式的文件。

详细用法如下：

```
usage: cli.py [-h] [--disable-cache] [--epub] [--epub-mixed] [--txt] [--txt-mixed] [--zh] [-t TRANSLATOR] url

positional arguments:
  url                   书的网址

options:
  -h, --help            show this help message and exit
  --disable-cache       关闭缓存
  --epub                生成epub
  --epub-mixed          生成原文混合版epub
  --txt                 生成txt
  --txt-mixed           生成原文混合版txt
  --zh                  翻译成中文
  -t TRANSLATOR, --translator TRANSLATOR
                        翻译器id
```

- 百度网页版，id：`baidu-web`。
- 百度开发者版，id：`baidu-vip`。
  - 需要环境变量`BAIDU_VIP_TRANSLATE_APPID`和`BAIDU_VIP_TRANSLATE_APPKEY`。
