import argparse
import logging
import sys
from pathlib import Path

sys.path.append(str(Path(__file__).parent.parent))

from app.provider import parse_url
from app.make import make_book


class Unbuffered(object):
    def __init__(self, stream):
        self.stream = stream

    def write(self, data):
        self.stream.write(data)
        self.stream.flush()

    def writelines(self, datas):
        self.stream.writelines(datas)
        self.stream.flush()

    def __getattr__(self, attr):
        return getattr(self.stream, attr)


logging.basicConfig(
    stream=Unbuffered(sys.stdout),
    level=logging.INFO,
    format="%(message)s",
)


def cli(args):
    parsed = parse_url(args.url)
    if not parsed:
        logging.info("无法解析网址，可能是因为格式错误或者不支持")
        exit(-1)

    provider_id, book_id = parsed

    make_book(
        provider_id=provider_id,
        book_id=book_id,
        lang="zh" if args.zh else "jp",
        start_index=args.start - 1,
        end_index=args.end - 1,
        cache_dir=Path("."),
        epub_enabled=args.epub,
        epub_mixed_enabled=args.epub_mixed,
        txt_enabled=args.txt,
        txt_mixed_enabled=args.txt_mixed,
    )

    logging.info("完成")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "url",
        help="书的网址",
    )
    parser.add_argument(
        "--zh",
        action="store_true",
        help="翻译成中文",
    )
    parser.add_argument(
        "--epub",
        action="store_true",
        help="生成epub",
    )
    parser.add_argument(
        "--epub-mixed",
        action="store_true",
        help="生成原文混合版epub",
    )
    parser.add_argument(
        "--txt",
        action="store_true",
        help="生成txt",
    )
    parser.add_argument(
        "--txt-mixed",
        action="store_true",
        help="生成原文混合版txt",
    )
    parser.add_argument(
        "--start",
        type=int,
        nargs="?",
        const=0,
        default=0,
        help="起始章节序号，默认为0",
    )
    parser.add_argument(
        "--end",
        type=int,
        nargs="?",
        const=65536,
        default=65536,
        help="结束章节序号，默认为65536",
    )
    parser.add_argument(
        "-t",
        "--translator",
        help="翻译器id",
    )

    cli(args=parser.parse_args())
