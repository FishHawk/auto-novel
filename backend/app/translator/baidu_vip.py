from hashlib import md5
import os
import random
from typing import List
import requests

from app.translator.baidu import BaiduBaseTranslate


def _make_md5(text, encoding="utf-8"):
    return md5(text.encode(encoding)).hexdigest()


class BaiduVipTranslate(BaiduBaseTranslate):
    translator_id = "baidu-vip"

    def __init__(self):
        self.session = requests.Session()
        self.appid = os.environ["BAIDU_VIP_TRANSLATE_APPID"]
        self.appkey = os.environ["BAIDU_VIP_TRANSLATE_APPKEY"]

    def _inner_translate(
        self,
        query: str,
        from_lang: str,
        to_lang: str,
    ) -> List[str]:
        salt = random.randint(32768, 65536)
        sign = _make_md5(self.appid + query + str(salt) + self.appkey)

        res = self.session.post(
            "http://api.fanyi.baidu.com/api/trans/vip/translate",
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            data={
                "appid": self.appid,
                "q": query,
                "from": from_lang,
                "to": to_lang,
                "salt": salt,
                "sign": sign,
            },
        )
        res.raise_for_status()

        json = res.json()
        if "error_code" in json:
            msg = json["error_code"]
            raise Exception(f"Baidu translator error: {msg}")
        else:
            result = []
            for data in json["trans_result"]:
                result.append(data["dst"])
            return result
