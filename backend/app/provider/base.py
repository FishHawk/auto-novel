from abc import ABC, abstractmethod

from app.cache import BookCache
from app.model import BookMetadata, Episode


class BookProvider(ABC):
    provider_id: str
    lang: str

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
        allow_request: bool,
    ) -> Episode | None:
        episode = cache.get_episode(
            lang=self.lang,
            episode_id=episode_id,
        )
        if not episode and allow_request:
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
