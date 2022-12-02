from pprint import pp

from app.provider.base import BookProvider
from app.provider.kakuyomu import Kakuyomu
from app.provider.syosetu import Syosetu
from app.provider.novelup import Novelup
from app.provider.hameln import Hameln
from app.provider.pixiv import Pixiv


class BaseTestProvider:
    provider: BookProvider
    benches_url: None
    benches_book: None
    benches_episode: None

    def test_parse_url(self):
        for url, book_id in self.benches_url:
            assert book_id == self.provider.__class__.extract_book_id_from_url(url)

    def test_get_book_metadata(self):
        for book_id in self.benches_book:
            metadata = self.provider._get_book_metadata(book_id)
            pp(metadata)

    def test_get_episode(self):
        for book_id, episode_id in self.benches_episode:
            episode = self.provider._get_episode(book_id, episode_id)
            pp(episode.paragraphs[0])
            pp(episode.paragraphs[-1])
            pp(len(episode.paragraphs))


class TestKakuyomu(BaseTestProvider):
    provider = Kakuyomu()
    benches_url = [
        (
            "https://kakuyomu.jp/works/16817139555217983105",
            "16817139555217983105",
        ),
        (
            "https://kakuyomu.jp/works/16817139555217983105/episodes/16817139555286132564",
            "16817139555217983105",
        ),
    ]
    benches_book = [
        "16816700429191462823",
        "1177354054880238351",
        "1177354054882961666",
    ]
    benches_episode = [
        ("16816700429191462823", "16816700429191679071"),
    ]


class TestSyosetu(BaseTestProvider):
    provider = Syosetu()
    benches_url = [
        ("https://ncode.syosetu.com/n9669bk", "n9669bk"),
        ("https://ncode.syosetu.com/n9669BK", "n9669bk"),
        ("https://novel18.syosetu.com/n9669BK", "n9669bk"),
    ]
    benches_book = [
        "n9669bk",
        "n8473hv",  # 介绍需要展开
        "n0916hw",  # 一篇完结
        "n5305eg",  # novel18重定向
    ]
    benches_episode = [
        ("n9669bk", "1"),
        ("n0916hw", "default"),
        ("n5305eg", "414"),  # novel18重定向
    ]


class TestNovelup(BaseTestProvider):
    provider = Novelup()
    benches_url = [
        ("https://novelup.plus/story/206612087", "206612087"),
        ("https://novelup.plus/story/206612087?p=2", "206612087"),
    ]
    benches_book = [
        "875360007",  # 目录很多页，被折叠
        "339045601",  # 目录1页
    ]
    benches_episode = [
        ("875360007", "711936781"),  # 嵌入了ruby tag实现罗马音
    ]


class TestHameln(BaseTestProvider):
    provider = Hameln()
    benches_url = [
        ("https://syosetu.org/novel/297874/", "297874"),
    ]
    benches_book = [
        "297874",
        "292106",  # 作者没有链接
        "303596",  # 一篇完结
    ]
    benches_episode = [
        ("297874", "1.html"),
        ("303596", "default"),  # 一篇完结
    ]


class TestPixiv(BaseTestProvider):
    provider = Pixiv()
    benches_url = [
        ("https://www.pixiv.net/novel/series/870363", "870363"),
        ("https://www.pixiv.net/novel/series/870363?p=5", "870363"),
    ]
    benches_book = [
        "9406879",  # 目录1页
        "870363",  # 目录很多页
    ]
    benches_episode = [
        ("9406879", "18304868"),  # 章节1页
        ("870363", "18199707"),  # 章节很多页
    ]
