from typing import Dict, List
from dataclasses import dataclass


@dataclass
class Author:
    name: str
    link: str


@dataclass
class TocEpisodeToken:
    title: str
    episode_id: str


@dataclass
class TocChapterToken:
    title: str
    level: int


@dataclass
class BookMetadata:
    title: str
    authors: List[Author]
    introduction: str
    toc: List[TocEpisodeToken | TocChapterToken]

    def to_query_list(self) -> List[str]:
        return [self.title, self.introduction] + [token.title for token in self.toc]

    def apply_translated_result(self, result_list: List[str]):
        self.title = result_list.pop(0)
        self.introduction = result_list.pop(0)
        for token, translated_title in zip(self.toc, result_list):
            token.title = translated_title


@dataclass
class Episode:
    paragraphs: List[str]


@dataclass
class Book:
    provider_id: str
    book_id: str
    lang: str
    metadata: BookMetadata
    episodes: Dict[str, Episode]
