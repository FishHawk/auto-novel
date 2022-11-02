# Web Novel Ebook Generator

[网络小说 epub/txt 生成器](https://books.fishhawk.top/)的后端，可以将日本网络小说翻译成中文并转换成电子书。

支持的站点：

- kakuyomu.jp
- syosetu.com

支持的格式：

- TXT
- EPUB

## 本地运行

1. 安装依赖：`pip install -r requirements.txt`。
2. 下载小说：`python backend/script/cli.py "小说的url" --epub --txt -l zh`。这会下载小说并翻译成中文，同时生成 EPUB 和 TXT 两种格式的文件。

详细用法如下：

```
usage: cli.py [-h] [--disable-cache] [--epub] [--txt] [-l LANGS [LANGS ...]] [-t TRANSLATOR] url

positional arguments:
  url                   book url

options:
  -h, --help            show this help message and exit
  --disable-cache       disable cache
  --epub                create epub
  --txt                 create txt
  -l LANGS [LANGS ...], --langs LANGS [LANGS ...]
                        the languages to translate
  -t TRANSLATOR, --translator TRANSLATOR
                        translator id
```
