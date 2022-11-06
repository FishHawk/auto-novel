from typing import List
from app.translator.base import Translator
from app.translator.baidu_web import BaiduWebTranslate
from app.translator.baidu_vip import BaiduVipTranslate

_TRANSLATORS: List[Translator] = [BaiduWebTranslate, BaiduVipTranslate]


def get_translator(name: str) -> Translator:
    for translator in _TRANSLATORS:
        if translator.translator_id == name:
            return translator()
    raise RuntimeError(f"Unknown translator name: {name}")


def get_default_translator() -> Translator:
    return BaiduWebTranslate()
    # return BaiduVipTranslate()
