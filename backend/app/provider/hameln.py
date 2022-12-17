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


class Hameln(BookProvider):
    provider_id = "hameln"
    session = requests.Session()

    def _get_book_metadata(self, book_id: str) -> BookMetadata:
        url = f"https://syosetu.org/novel/${book_id}"
        res = self.session.get(url, headers=_HEADERS)
        res.raise_for_status()
        soup = bs4.BeautifulSoup(res.text, "html.parser")

        if soup.find("span", {"itemprop": "name"}):
            return self._parse_metadata_normal(soup)
        else:
            return self._parse_metadata_single(soup)

    def _parse_metadata_normal(self, soup: bs4.BeautifulSoup) -> BookMetadata:
        book_title = soup.find("span", {"itemprop": "name"}).text

        authors_tag = soup.find("span", {"itemprop": "author"})
        authors = [
            Author(name=a.text, link="https:" + a["href"])
            for a in authors_tag.find_all("a")
        ]
        if not authors:
            authors = [Author(name=authors_tag.text, link="")]

        introduction = soup.find_all("div", {"class": "ss"})[1].text

        toc = []
        for tr_tag in soup.find("table").find_all("tr"):
            a_tag = tr_tag.find("a")
            if a_tag:
                title = a_tag.text
                episode_id = a_tag["href"][2:]
                toc.append(TocEpisodeToken(title=title, episode_id=episode_id))
            else:
                toc.append(TocChapterToken(title=tr_tag.text, level=1))

        return BookMetadata(
            title=book_title,
            authors=authors,
            introduction=introduction,
            toc=toc,
        )

    def _parse_metadata_single(self, soup: bs4.BeautifulSoup) -> BookMetadata:
        ss_list = soup.find_all("div", {"class": "ss"})
        book_title = ss_list[0].a.text
        authors = [Author(name=ss_list[0].a.find_next("a").text, link="")]
        introduction = ss_list[1].find(text=True, recursive=False)

        return BookMetadata(
            title=book_title,
            authors=authors,
            introduction=introduction,
            toc=[TocEpisodeToken(title="", episode_id="default")],
        )

    def _get_episode(
        self,
        book_id: str,
        episode_id: str,
    ) -> Episode:
        if episode_id == "default":
            url = f"https://syosetu.org/novel/{book_id}/"
        else:
            url = f"https://syosetu.org/novel/{book_id}/{episode_id}"
        res = self.session.get(url, headers=_HEADERS)
        res.raise_for_status()
        soup = bs4.BeautifulSoup(res.text, "html.parser")

        paragraphs = [p.text for p in soup.find("div", {"id": "honbun"}).find_all("p")]

        return Episode(paragraphs=paragraphs)
