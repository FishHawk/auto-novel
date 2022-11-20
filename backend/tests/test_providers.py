import os
from unittest import mock
from app.provider.kakuyomu import Kakuyomu
from app.provider.syosetu import Syosetu
from app.provider.novelup import Novelup


class TestClassKakuyomu:
    provider = Kakuyomu()

    def test_parse_url(self):
        bench = [
            ("https://kakuyomu.jp/works/16817139555217983105", "16817139555217983105"),
            (
                "https://kakuyomu.jp/works/16817139555217983105/episodes/16817139555286132564",
                "16817139555217983105",
            ),
        ]
        for url, book_id in bench:
            assert book_id == Kakuyomu.extract_book_id_from_url(url)

    def test_get_book_metadata(self):
        self.provider._get_book_metadata("16816700429191462823")
        self.provider._get_book_metadata("1177354054880238351")
        self.provider._get_book_metadata("1177354054882961666")

    def test_get_episode(self):
        self.provider._get_episode("16816700429191462823", "16816700429191679071")


@mock.patch.dict(os.environ, {"HTTPS_PROXY": "http://localhost:7890"})
class TestClassSyosetu:
    provider = Syosetu()

    def test_parse_url(self):
        bench = [
            ("https://ncode.syosetu.com/n9669bk", "n9669bk"),
            ("https://ncode.syosetu.com/n9669BK", "n9669bk"),
            ("https://novel18.syosetu.com/n9669BK", "n9669bk"),
        ]
        for url, book_id in bench:
            assert book_id == Syosetu.extract_book_id_from_url(url)

    def test_get_book_metadata(self):
        # self.provider._get_book_metadata("n9669bk")
        # self.provider._get_book_metadata("n8473hv")  # 介绍需要展开
        # self.provider._get_book_metadata("n0916hw")  # 一篇完结
        self.provider._get_book_metadata("n5305eg")  # novel18重定向

    def test_get_episode(self):
        # self.provider._get_episode("n9669bk", "1")
        # self.provider._get_episode("n0916hw", "default")
        self.provider._get_episode("n5305eg", "414")  # novel18重定向


class TestClassNovelup:
    provider = Novelup()

    def test_parse_url(self):
        bench = [
            ("https://novelup.plus/story/206612087", "206612087"),
            ("https://novelup.plus/story/206612087?p=2", "206612087"),
        ]
        for url, book_id in bench:
            assert book_id == Novelup.extract_book_id_from_url(url)

    def test_get_book_metadata(self):
        self.provider._get_book_metadata("875360007")  # 目录很多页，被折叠
        self.provider._get_book_metadata("339045601")  # 目录1页

    def test_get_episode(self):
        self.provider._get_episode("875360007", "711936781")  # 嵌入了ruby tag实现罗马音
