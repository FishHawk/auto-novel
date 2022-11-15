import re
from typing import List

import js2py
import requests

from app.translator.baidu import BaiduBaseTranslate

_JS_CODE = """
function a(r, o) {
    for (var t = 0; t < o.length - 2; t += 3) {
        var a = o.charAt(t + 2);
        a = a >= "a" ? a.charCodeAt(0) - 87 : Number(a),
        a = "+" === o.charAt(t + 1) ? r >>> a: r << a,
        r = "+" === o.charAt(t) ? r + a & 4294967295 : r ^ a
    }
    return r
}
var C = null;
var token = function(r, _gtk) {
    var o = r.length;
    o > 30 && (r = "" + r.substr(0, 10) + r.substr(Math.floor(o / 2) - 5, 10) + r.substring(r.length, r.length - 10));
    var t = void 0,
    t = null !== C ? C: (C = _gtk || "") || "";
    for (var e = t.split("."), h = Number(e[0]) || 0, i = Number(e[1]) || 0, d = [], f = 0, g = 0; g < r.length; g++) {
        var m = r.charCodeAt(g);
        128 > m ? d[f++] = m: (2048 > m ? d[f++] = m >> 6 | 192 : (55296 === (64512 & m) && g + 1 < r.length && 56320 === (64512 & r.charCodeAt(g + 1)) ? (m = 65536 + ((1023 & m) << 10) + (1023 & r.charCodeAt(++g)), d[f++] = m >> 18 | 240, d[f++] = m >> 12 & 63 | 128) : d[f++] = m >> 12 | 224, d[f++] = m >> 6 & 63 | 128), d[f++] = 63 & m | 128)
    }
    for (var S = h,
    u = "+-a^+6",
    l = "+-3^+b+-f",
    s = 0; s < d.length; s++) S += d[s],
    S = a(S, u);
    return S = a(S, l),
    S ^= i,
    0 > S && (S = (2147483647 & S) + 2147483648),
    S %= 1e6,
    S.toString() + "." + (S ^ h)
}
"""

_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36"
}


class BaiduWebTranslate(BaiduBaseTranslate):
    translator_id = "baidu-web"

    def __init__(self, from_lang: str, to_lang: str) -> None:
        super().__init__(from_lang, to_lang)

        self.session = requests.Session()
        self.token = None
        self.gtk = None
        self.javascript = js2py.eval_js(_JS_CODE)

        # 必须要加载两次保证token是最新的，否则会出现998的错误
        self._load_main_page()
        self._load_main_page()

    def _load_main_page(self):
        url = "https://fanyi.baidu.com"
        res = self.session.get(url, headers=_HEADERS)
        self.token = re.findall(r"token: '(.*?)',", res.text)[0]
        self.gtk = re.findall(r'window.gtk = "(.*?)";', res.text)[0]

    def _inner_translate(self, query: str) -> List[str]:
        url = "https://fanyi.baidu.com/v2transapi"
        sign = self.javascript(query, self.gtk)
        data = {
            "from": self.from_lang,
            "to": self.to_lang,
            "query": query,
            "simple_means_flag": 3,
            "sign": sign,
            "token": self.token,
            "domain": "common",
        }
        res = self.session.post(url=url, data=data, headers=_HEADERS)
        res.raise_for_status()

        json = res.json()
        if "error" in json:
            # 998表示需要重新加载主页获取新的token
            # 1022可能表示超出使用限制
            msg = json["error"]
            raise Exception(f"Baidu translator error: {msg}")
        else:
            result = []
            for data in json["trans_result"]["data"]:
                result.append(data["dst"])
            return result
