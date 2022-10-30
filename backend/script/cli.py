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

    if args.langs:
        if args.translator:
            translator = get_translator(args.translator)
        else:
            translator = get_default_translator()

        for lang in args.langs:
            if lang == book.lang:
                continue

            book_translated = translator.translate_book(
                book=book,
                lang=lang,
                cache=cache,
            )

            make_book(
                output_path=output_path,
                book=book_translated,
                epub_enabled=args.epub,
                txt_enabled=args.txt,
            )

    logging.info("完成")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "url",
        help="book url",
    )
    parser.add_argument(
        "--disable-cache",
        action="store_true",
        help="disable cache",
    )
    parser.add_argument(
        "--epub",
        action="store_true",
        help="create epub",
    )
    parser.add_argument(
        "--txt",
        action="store_true",
        help="create txt",
    )
    parser.add_argument(
        "-l",
        "--langs",
        nargs="+",
        help="the languages to translate",
    )
    parser.add_argument(
        "-t",
        "--translator",
        help="translator id",
    )

    args = parser.parse_args()

    cli(args)
