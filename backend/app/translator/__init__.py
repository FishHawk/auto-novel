from typing import List

from app.translator.base import Translator
from app.translator.baidu_web import BaiduWebTranslate
from app.translator.baidu_vip import BaiduVipTranslate

_TRANSLATORS: List[Translator] = [
    BaiduWebTranslate,
    BaiduVipTranslate,
]


DEFAULT_TRANSLATOR_ID = "baidu-web"


def get_translator(name: str, from_lang: str, to_lang: str) -> Translator:
    for translator in _TRANSLATORS:
        if translator.translator_id == name:
            return translator(from_lang=from_lang, to_lang=to_lang)
    raise RuntimeError(f"Unknown translator name: {name}")
