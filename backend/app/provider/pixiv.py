import json
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

    base_url = "https://www.pixiv.net"
    session = requests.Session()
    headers = _create_headers()
    proxies = _create_proxies()

    @staticmethod
    def extract_book_id_from_url(url: str) -> str | None:
        match = re.search(r"pixiv.net/novel/series/([0-9]+)", url)
        book_id = match.group(1) if match else None
        if book_id is None:
            match = re.search(r"pixiv.net/novel/show.php\?id=([0-9]+)", url)
            book_id = "s" + match.group(1) if match else None
        return book_id

    @staticmethod
    def build_url_from_book_id(book_id: str) -> str | None:
        if book_id.startswith("s"):
            return f"{Pixiv.base_url}/novel/show.php?id={book_id[1:]}"
        else:
            return f"{Pixiv.base_url}/novel/series/{book_id}"

    def _fetch(self, url):
        res = self.session.get(
            self.base_url + url,
            headers=self.headers,
            proxies=self.proxies,
        )
        res.raise_for_status()
        return res

    @staticmethod
    def _build_authors(user_name: str, user_id: str):
        link = f"{Pixiv.base_url}/users/{user_id}"
        return [Author(name=user_name, link=link)]

    def _get_book_metadata(self, book_id: str) -> BookMetadata:
        if book_id.startswith("s"):
            return self._parse_metadata_single(book_id[1:])
        else:
            return self._parse_metadata_serial(book_id)

    def _parse_metadata_serial(self, book_id: str) -> BookMetadata:
        res = self._fetch(f"/ajax/novel/series/{book_id}/content_titles")
        toc = [
            TocEpisodeToken(title=obj["title"], episode_id=obj["id"])
            for obj in res.json()["body"]
        ]

        res = self._fetch(f"/ajax/novel/series/{book_id}")
        obj = res.json()["body"]

        return BookMetadata(
            title=obj["title"],
            authors=self._build_authors(obj["userName"], obj["userId"]),
            introduction=obj["caption"],
            toc=toc,
        )

    def _parse_metadata_single(self, episode_id: str) -> BookMetadata:
        res = self._fetch(f"/novel/show.php?id={episode_id}")
        soup = bs4.BeautifulSoup(res.text, "html.parser")

        obj = json.loads(soup.find("meta", {"id": "meta-preload-data"})["content"])
        obj = obj["novel"][episode_id]
        assert obj["seriesNavData"] is None

        return BookMetadata(
            title=obj["title"],
            authors=self._build_authors(obj["userName"], obj["userId"]),
            introduction=obj["description"].replace("<br />", "\n"),
            toc=[TocEpisodeToken(title="", episode_id=episode_id)],
        )

    def _get_episode(
        self,
        book_id: str,
        episode_id: str,
    ) -> Episode:
        res = self._fetch(f"/novel/show.php?id={episode_id}")
        soup = bs4.BeautifulSoup(res.text, "html.parser")

        obj = json.loads(soup.find("meta", {"id": "meta-preload-data"})["content"])
        obj = obj["novel"][episode_id]

        return Episode(paragraphs=obj["content"].splitlines())
