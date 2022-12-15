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


class Novelup(BookProvider):
    provider_id = "novelup"
    lang = "jp"
    session = requests.Session()
    def _get_book_metadata(self, book_id: str) -> BookMetadata:
        url = f"https://novelup.plus/story/{book_id}"
        res = self.session.get(url)
        res.raise_for_status()
        soup = bs4.BeautifulSoup(res.text, "html.parser")

        book_title = soup.find("div", {"class": "novel_title"}).find("h1").text

        authors = [
            Author(name=a.text, link=a["href"])
            for a in soup.find("div", {"class": "novel_author"}).findAll("a")
        ]

        introduction = soup.find("div", {"class": "novel_synopsis"}).text

        page_tags = soup.find("ul", {"class": "pagination"}).find_all("a")
        if page_tags:
            link = page_tags[-1]["href"]
            match = re.search(r"\?p=([0-9]+)", link)
            page_total = int(match.group(1))
        else:
            page_total = 1

        toc = []

        def append_page(soup: bs4.BeautifulSoup):
            for li_tag in soup.find("div", {"class": "episode_list"}).find_all("li"):
                class_attr = li_tag["class"]
                if "chapter" in class_attr:
                    title = li_tag.find("cite").text
                    if title.strip():
                        toc.append(TocChapterToken(title=title, level=1))
                else:
                    a_tag = li_tag.find("a")
                    title = a_tag.text.strip()
                    episode_id = a_tag["href"].split("/")[-1]
                    toc.append(TocEpisodeToken(title=title, episode_id=episode_id))

        append_page(soup)
        for page in range(2, page_total + 1):
            url = f"https://novelup.plus/story/{book_id}?p={page}"
            res = self.session.get(url)
            res.raise_for_status()
            soup = bs4.BeautifulSoup(res.text, "html.parser")
            append_page(soup)

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
        url = f"https://novelup.plus/story/{book_id}/{episode_id}"
        res = self.session.get(url)
        res.raise_for_status()
        soup = bs4.BeautifulSoup(res.text, "html.parser")

        content = soup.find("div", {"class": "content"}).find("p")
        paragraphs = content.text.splitlines()

        return Episode(paragraphs=paragraphs)
