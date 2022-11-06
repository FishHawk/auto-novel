from typing import List, Tuple

from app.provider.base import BookProvider
from app.provider.kakuyomu import Kakuyomu
from app.provider.syosetu import Syosetu

_PROVIDER_LIST: List[BookProvider] = [
    Kakuyomu,
    Syosetu,
]


def parse_url_as_provider_and_book_id(
    url: str,
) -> Tuple[BookProvider, str] | None:
    for source in _PROVIDER_LIST:
        book_id = source.extract_book_id_from_url(url)
        if book_id is not None:
            return (source(), book_id)
    return None


def build_url(
    provider_id: str,
    book_id: str,
) -> str | None:
    for provider_class in _PROVIDER_LIST:
        if provider_id == provider_class.provider_id:
            return provider_class.build_url_from_book_id(book_id=book_id)
    return None


def get_provider(
    provider_id: str,
) -> BookProvider | None:
    for provider_class in _PROVIDER_LIST:
        if provider_id == provider_class.provider_id:
            return provider_class()
    return None
