import re

import requests
import bs4

from app.provider.base import BookProvider
from app.model import (
    Author,
    TocEpisodeToken,
    TocChapterToken,
    BookMetadata,
    Episode,
)


class Kakuyomu(BookProvider):
    provider_id = "kakuyomu"
    lang = "jp"
    session = requests.Session()

    @staticmethod
    def extract_book_id_from_url(url: str) -> str | None:
        match = re.search(r"kakuyomu.jp/works/([0-9]+)", url)
        if match is None:
            return None
        else:
            return match.group(1)

    @staticmethod
    def build_url_from_book_id(book_id: str) -> str | None:
        return f"https://kakuyomu.jp/works/{book_id}"

    def _get_book_metadata(self, book_id: str) -> BookMetadata:
        url = f"https://kakuyomu.jp/works/{book_id}"
        res = self.session.get(url)
        res.raise_for_status()
        soup = bs4.BeautifulSoup(res.text, "html.parser")

        book_title = soup.find("h1", {"id": "workTitle"}).find("a").text

        authors = [
            Author(name=a.text, link=f"https://kakuyomu.jp{a['href']}")
            for a in soup.find("span", {"id": "workAuthor-activityName"}).findAll("a")
        ]

        introduction = soup.find("p", {"id": "introduction"}).text

        toc = []
        for li_tag in soup.find("ol", {"class": "widget-toc-items"}).find_all("li"):
            class_attr = li_tag["class"]
            if "widget-toc-chapter" in class_attr:
                title = li_tag.find("span").text
                if "widget-toc-level1" in class_attr:
                    toc.append(TocChapterToken(title=title, level=1))
                elif "widget-toc-level2" in class_attr:
                    toc.append(TocChapterToken(title=title, level=2))
                else:
                    raise RuntimeError("parse error")
            elif "widget-toc-episode" in class_attr:
                title = li_tag.find("span").text
                episode_id = li_tag.find("a")["href"].split("/")[-1]
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
        url = f"https://kakuyomu.jp/works/{book_id}/episodes/{episode_id}"
        res = self.session.get(url)
        res.raise_for_status()
        soup = bs4.BeautifulSoup(res.text, "html.parser")

        paragraphs = [
            p.text
            for p in soup.find("div", {"class": "widget-episodeBody"}).find_all("p")
        ]

        return Episode(paragraphs=paragraphs)
