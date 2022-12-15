from typing import List

from app.provider.base import BookProvider
from app.provider.kakuyomu import Kakuyomu
from app.provider.syosetu import Syosetu
from app.provider.novelup import Novelup
from app.provider.hameln import Hameln
from app.provider.pixiv import Pixiv

_PROVIDER_LIST: List[BookProvider] = [
    Kakuyomu,
    Syosetu,
    Novelup,
    Hameln,
    Pixiv,
]


def get_provider(provider_id: str) -> BookProvider | None:
    for provider_class in _PROVIDER_LIST:
        if provider_id == provider_class.provider_id:
            return provider_class()
    return None
