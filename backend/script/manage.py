import logging
import sys
from pathlib import Path

sys.path.append(str(Path(__file__).parent.parent))

from app.provider import get_provider
from app.cache import BookCache


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


# 刷新库中所有损坏的metadata。
# 通常是由于metadata格式更新造成的。
def refresh_metadata():
    books_path = Path("/books")
    all_zip_paths = sorted(
        books_path.glob("*.zip"),
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )

    for zip_path in all_zip_paths:
        try:
            provider_id, book_id = zip_path.stem.split(".")
        except ValueError:
            continue

        cache = BookCache(zip_path)
        metadata = cache.get_book_metadata("jp")

        if not metadata:
            logging.info(
                "元数据损坏,尝试刷新:%s/%s",
                provider_id,
                book_id,
            )
            provider = get_provider(provider_id)
            try:
                metadata = provider.get_book_metadata(
                    book_id=book_id,
                    cache=cache,
                )
            except Exception as exception:
                logging.warning(
                    "元数据刷新失败:%s/%s",
                    provider_id,
                    book_id,
                )
                logging.warning(exception, exc_info=True)


if __name__ == "__main__":
    refresh_metadata()
