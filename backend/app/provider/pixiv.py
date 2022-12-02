import logging
import os
import re

import requests
import bs4

from app.provider.base import BookProvider
from app.model import (
    Author,
    TocEpisodeToken,
    BookMetadata,
    Episode,
)


def _create_headers():
    headers = {
        "user-agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36",
    }
    phpsessid = os.environ.get("PIXIV_COOKIE_PHPSESSID")
    if phpsessid:
        headers["cookie"] = f"PHPSESSID={phpsessid}"
    return headers


def _create_proxies():
    proxies = {}
    if "HTTPS_PROXY" in os.environ:
        proxies["https"] = os.environ["HTTPS_PROXY"]
    return proxies


class Pixiv(BookProvider):
    provider_id = "pixiv"
    lang = "jp"
    session = requests.Session()
    headers = _create_headers()
    proxies = _create_proxies()

    @staticmethod
    def extract_book_id_from_url(url: str) -> str | None:
        match = re.search(r"pixiv.net/novel/series/([0-9]+)", url)
        if match is None:
            return None
        else:
            return match.group(1)

    @staticmethod
    def build_url_from_book_id(book_id: str) -> str | None:
        return f"https://www.pixiv.net/novel/series/{book_id}"

    def _fetch(self, url):
        res = self.session.get(
            "https://www.pixiv.net" + url,
            headers=self.headers,
            proxies=self.proxies,
        )
        res.raise_for_status()
        return res

    def _get_book_metadata(self, book_id: str) -> BookMetadata:
        res = self._fetch(f"/ajax/novel/series/{book_id}")
        body = res.json()["body"]

        book_title = body["title"]

        authors = [
            Author(
                name=body["userName"],
                link="https://www.pixiv.net/users/" + body["userId"],
            )
        ]

        introduction = body["caption"]

        res = self._fetch(f"/ajax/novel/series/{book_id}/content_titles")
        body = res.json()["body"]

        toc = []
        for obj in body:
            toc.append(TocEpisodeToken(title=obj["title"], episode_id=obj["id"]))

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
        res = self._fetch(f"/novel/show.php?id={episode_id}")
        soup = bs4.BeautifulSoup(res.text, "html.parser")

        content = soup.find("meta", {"id": "meta-preload-data"})["content"]
        content = content.split('"content":"', 1)[1]
        content = content.split('","coverUrl":"', 1)[0]
        paragraphs = content.split("\\n")
        return Episode(paragraphs=paragraphs)
