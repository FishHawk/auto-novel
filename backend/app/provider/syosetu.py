import os
import re

import bs4
import requests

from app.provider.base import BookProvider
from app.model import (
    Author,
    TocEpisodeToken,
    TocChapterToken,
    BookMetadata,
    Episode,
)

_HEADERS = {
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36",
}


def _create_session():
    session = requests.Session()
    session.cookies.set("over18", "yes", domain=".syosetu.com")
    return session


def _create_proxies():
    proxies = {}
    if "HTTPS_PROXY" in os.environ:
        proxies["https"] = os.environ["HTTPS_PROXY"]
    return proxies


class Syosetu(BookProvider):
    provider_id = "syosetu"
    lang = "jp"
    session = _create_session()
    proxies = _create_proxies()

    @staticmethod
    def extract_book_id_from_url(url: str) -> str | None:
        match = re.search(r"syosetu.com/([A-Za-z0-9]+)", url)
        if match is None:
            return None
        else:
            return match.group(1).lower()

    @staticmethod
    def build_url_from_book_id(book_id: str) -> str | None:
        return f"https://ncode.syosetu.com/{book_id}"

    def _get_book_metadata(self, book_id: str) -> BookMetadata:
        url = f"https://ncode.syosetu.com/{book_id}"
        res = self.session.get(url, headers=_HEADERS, proxies=self.proxies)
        res.raise_for_status()
        soup = bs4.BeautifulSoup(res.text, "html.parser")

        book_title = soup.find("p", {"class": "novel_title"}).text

        authors = [
            Author(name=a.text, link=a["href"])
            for a in soup.find("div", {"class": "novel_writername"}).findAll("a")
        ]

        if not soup.find("div", {"class": "index_box"}):
            return BookMetadata(
                title=book_title,
                authors=authors,
                introduction="",
                toc=[TocEpisodeToken(title="", episode_id="default")],
            )

        introduction = soup.find("div", id="novel_ex").text

        toc = []
        for child in soup.find("div", {"class": "index_box"}).find_all(recursive=False):
            if child.name == "div":
                toc.append(TocChapterToken(title=child.text, level=1))
            elif child.name == "dl":
                title = child.find("a").text
                episode_id = child.find("a")["href"].split("/")[-2]
                toc.append(TocEpisodeToken(title=title, episode_id=episode_id))
            else:
                raise RuntimeError("parse error")

        return BookMetadata(
            title=book_title,
            authors=authors,
            introduction=introduction,
            toc=toc,
        )

    def _get_episode(
        self,
        book_id: str,
        episode_id: str,
    ) -> Episode:
        if episode_id == "default":
            url = f"https://ncode.syosetu.com/{book_id}"
        else:
            url = f"https://ncode.syosetu.com/{book_id}/{episode_id}"
        res = self.session.get(url, headers=_HEADERS, proxies=self.proxies)
        res.raise_for_status()
        soup = bs4.BeautifulSoup(res.text, "html.parser")

        paragraphs = [
            p.text for p in soup.find("div", {"id": "novel_honbun"}).find_all("p")
        ]

        return Episode(paragraphs=paragraphs)
