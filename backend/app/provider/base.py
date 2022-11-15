import logging
from abc import ABC, abstractmethod

from app.cache import BookCache
from app.model import Book, BookMetadata, Episode, TocEpisodeToken


class BookProvider(ABC):
    provider_id: str
    lang: str

    @staticmethod
    @abstractmethod
    def extract_book_id_from_url(url: str) -> str | None:
        pass

    @staticmethod
    @abstractmethod
    def build_url_from_book_id(book_id: str) -> str | None:
        pass

    @abstractmethod
    def _get_book_metadata(self, book_id: str) -> BookMetadata:
        pass

    @abstractmethod
    def _get_episode(self, book_id: str, episode_id: str) -> Episode:
        pass

    def get_book_metadata(
        self,
        book_id: str,
        cache: BookCache,
    ) -> BookMetadata:
        metadata = cache.get_book_metadata(
            lang=self.lang,
        )
        if not metadata:
            metadata = self._get_book_metadata(
                book_id=book_id,
            )
            cache.save_book_metadata(
                lang=self.lang,
                metadata=metadata,
            )
        return metadata

    def get_episode(
        self,
        book_id: str,
        episode_id: str,
        cache: BookCache,
        cache_only: bool,
    ) -> Episode | None:
        episode = cache.get_episode(
            lang=self.lang,
            episode_id=episode_id,
        )
        if not episode and not cache_only:
            episode = self._get_episode(
                book_id=book_id,
                episode_id=episode_id,
            )
            cache.save_episode(
                lang=self.lang,
                episode_id=episode_id,
                episode=episode,
            )
        return episode

    def get_book(
        self,
        book_id: str,
        cache: BookCache | None = None,
        start_index: int = 0,
    ) -> Book:
        logging.info(
            "获取元数据:%s/%s",
            self.provider_id,
            book_id,
        )

        metadata = self.get_book_metadata(
            book_id=book_id,
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
                "获取章节:%d/%d %s/%s/%s",
                index + 1,
                len(episode_ids),
                self.provider_id,
                book_id,
                episode_id,
            )
            try:
                episode = self.get_episode(
                    book_id=book_id,
                    episode_id=episode_id,
                    cache=cache,
                    cache_only=index < start_index,
                )
                episodes[episode_id] = episode
            except Exception as exception:
                logging.warning(
                    "获取章节失败:%s/%s/%s",
                    self.provider_id,
                    book_id,
                    episode_id,
                )
                logging.warning(exception, exc_info=True)

        return Book(
            book_id=book_id,
            provider=self.provider_id,
            lang=self.lang,
            metadata=metadata,
            episodes=episodes,
        )
