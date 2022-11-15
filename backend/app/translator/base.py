from abc import ABC, abstractmethod
import copy
import logging
from typing import List

from app.cache import BookCache
from app.model import Book, BookMetadata, Episode, TocEpisodeToken


class Translator(ABC):
    translator_id: str

    def __init__(self, from_lang: str, to_lang: str) -> None:
        self.from_lang = from_lang
        self.to_lang = to_lang

    @abstractmethod
    def _translate(
        self,
        query_list: List[str],
    ) -> List[str]:
        pass

    def _translate_metadata(
        self,
        metadata: BookMetadata,
    ) -> BookMetadata:
        metadata = copy.deepcopy(metadata)

        query_list = [
            metadata.title,
            metadata.introduction,
        ] + [token.title for token in metadata.toc]

        result_list = self._translate(query_list=query_list)

        metadata.title = result_list.pop(0)
        metadata.introduction = result_list.pop(0)
        for token, translated_title in zip(metadata.toc, result_list):
            token.title = translated_title

        return metadata

    def _translate_episode(
        self,
        episode: Episode,
    ) -> Episode | None:
        query_list = [text for text in episode.paragraphs]
        result_list = self._translate(query_list=query_list)
        return Episode(paragraphs=result_list)

    def translate_metadata(
        self,
        metadata: BookMetadata,
        cache: BookCache,
    ) -> BookMetadata:
        translated_metadata = cache.get_book_metadata(
            lang=self.to_lang,
        )
        if not translated_metadata:
            translated_metadata = self._translate_metadata(
                metadata=metadata,
            )
            cache.save_book_metadata(
                lang=self.to_lang,
                metadata=translated_metadata,
            )
        return translated_metadata

    def translate_episode(
        self,
        episode_id: str,
        episode: Episode | None,
        cache: BookCache,
        cache_only: bool,
    ) -> Episode | None:
        translated_episode = cache.get_episode(
            lang=self.to_lang,
            episode_id=episode_id,
        )
        if not translated_episode and episode and not cache_only:
            translated_episode = self._translate_episode(
                episode=episode,
            )
            cache.save_episode(
                lang=self.to_lang,
                episode_id=episode_id,
                episode=translated_episode,
            )
        return translated_episode

    def translate_book(
        self,
        book: Book,
        cache: BookCache,
        start_index: int = 0,
    ) -> Book:
        logging.info(
            "翻译元数据:%s/%s",
            book.provider,
            book.book_id,
        )

        metadata = self.translate_metadata(
            metadata=book.metadata,
            cache=cache,
        )

        episode_ids = [
            token.episode_id
            for token in metadata.toc
            if isinstance(token, TocEpisodeToken)
        ]

        episodes = {}
        for index, episode_id in enumerate(episode_ids):
            logging.info(
                "翻译章节:%d/%d %s/%s/%s",
                index + 1,
                len(episode_ids),
                book.provider,
                book.book_id,
                episode_id,
            )
            try:
                episode = self.translate_episode(
                    episode_id=episode_id,
                    episode=book.episodes.get(episode_id),
                    cache=cache,
                    cache_only=index < start_index,
                )
                if not episode:
                    logging.info(
                        "跳过缺失章节:%s/%s/%s",
                        book.name,
                        book.book_id,
                        episode_id,
                    )
                episodes[episode_id] = episode
            except Exception as exception:
                logging.warning(
                    "翻译章节失败:%s/%s/%s",
                    book.provider,
                    book.book_id,
                    episode_id,
                )
                logging.warning(exception, exc_info=True)

        return Book(
            provider=book.provider,
            book_id=book.book_id,
            lang=self.to_lang,
            metadata=metadata,
            episodes=episodes,
        )
