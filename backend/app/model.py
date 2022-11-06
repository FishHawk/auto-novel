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


@dataclass
class Episode:
    paragraphs: List[str]


@dataclass
class Book:
    provider: str
    book_id: str
    lang: str
    metadata: BookMetadata
    episodes: Dict[str, Episode]
