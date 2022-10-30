from app.provider.kakuyomu import Kakuyomu
from app.provider.syosetu import Syosetu


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

    def test_get_book(self):
        # self.source.get_book('16816700429191462823')
        # self.provider.get_book_metadata('1177354054880238351')
        self.provider.get_book_metadata(1177354054882961666)

    def test_get_episode(self):
        self.provider.get_episode("16816700429191462823", "16816700429191679071")


class TestClassSyosetu:
    provider = Syosetu()

    def test_parse_url(self):
        bench = [
            ("https://ncode.syosetu.com/n9669bk", "n9669bk"),
            ("https://ncode.syosetu.com/n9669BK", "n9669bk"),
        ]
        for url, book_id in bench:
            assert book_id == Kakuyomu.extract_book_id_from_url(url)

    def test_get_book(self):
        # self.provider.get_book_metadata("n9669bk")
        # self.provider.get_book_metadata("n8473hv")  # 介绍需要展开
        self.provider.get_book_metadata("n0916hw")  # 一篇完结

    def test_get_episode(self):
        self.provider.get_episode("n9669bk", "1")
        self.provider.get_episode("n0916hw", "default")
