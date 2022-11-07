import argparse
import logging
import sys
from pathlib import Path

sys.path.append(str(Path(__file__).parent.parent))

from app.provider import parse_url_as_provider_and_book_id
from app.translator import get_default_translator, get_translator
from app.cache import BookCache
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
    parsed = parse_url_as_provider_and_book_id(args.url)
    if not parsed:
        logging.info("无法解析网址，可能是因为格式错误或者不支持")
        exit(-1)

    provider, book_id = parsed

    output_path = Path(".")

    cache = None
    if not args.disable_cache:
        cache = BookCache(
            cache_path=output_path / f"{provider.provider_id}.{book_id}.zip",
        )

    book = provider.get_book(
        book_id=book_id,
        cache=cache,
    )

    make_book(
        output_path=output_path,
        book=book,
        epub_enabled=args.epub,
        txt_enabled=args.txt,
    )

    if args.zh:
        if args.translator:
            translator = get_translator(args.translator)
        else:
            translator = get_default_translator()

        translated_book = translator.translate_book(
            book=book,
            lang="zh",
            cache=cache,
        )

        make_book(
            output_path=output_path,
            book=translated_book,
            secondary_book=book,
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
        "--disable-cache",
        action="store_true",
        help="关闭缓存",
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
        "--zh",
        action="store_true",
        help="翻译成中文",
    )
    parser.add_argument(
        "-t",
        "--translator",
        help="翻译器id",
    )

    args = parser.parse_args()

    cli(args)
