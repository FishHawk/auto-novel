from abc import ABC, abstractmethod
import copy
import logging
from typing import List

from app.cache import BookCache
from app.model import Book, BookMetadata, Episode, TocEpisodeToken


class Translator(ABC):
    translator_id: str

    @abstractmethod
    def _translate(
        self,
        query_list: List[str],
        from_lang: str,
        to_lang: str,
    ) -> List[str]:
        pass

    def _translate_metadata(
        self, metadata: BookMetadata, from_lang: str, to_lang: str
    ) -> BookMetadata:
        metadata = copy.deepcopy(metadata)

        query_list = [
            metadata.title,
            metadata.introduction,
        ] + [token.title for token in metadata.toc]

        result_list = self._translate(
            query_list=query_list,
            from_lang=from_lang,
            to_lang=to_lang,
        )

        metadata.title = result_list.pop(0)
        metadata.introduction = result_list.pop(0)
        for token, translated_title in zip(metadata.toc, result_list):
            token.title = translated_title

        return metadata

    def _translate_episode(
        self, episode: Episode, from_lang: str, to_lang: str
    ) -> Episode | None:
        query_list = [text for text in episode.paragraphs]

        result_list = self._translate(
            query_list=query_list,
            from_lang=from_lang,
            to_lang=to_lang,
        )

        return Episode(paragraphs=result_list)

    def translate_book(self, book: Book, lang: str, cache: BookCache | None) -> Book:
        logging.info(
            "翻译元数据:%s/%s",
            book.provider,
            book.book_id,
        )

        if cache:
            metadata = cache.get_book_metadata(
                lang=lang,
            )
        if not metadata:
            metadata = self._translate_metadata(
                metadata=book.metadata,
                from_lang=book.lang,
                to_lang=lang,
            )
            cache.save_book_metadata(lang=lang, metadata=metadata)

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
                if cache:
                    episode = cache.get_episode(
                        lang=lang,
                        episode_id=episode_id,
                    )
                if not episode:
                    episode_origin = book.episodes.get(episode_id)
                    if episode_origin:
                        episode = self._translate_episode(
                            episode=book.episodes[episode_id],
                            from_lang=book.lang,
                            to_lang=lang,
                        )
                        cache.save_episode(
                            lang=lang,
                            episode_id=episode_id,
                            episode=episode,
                        )
                    else:
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
            lang=lang,
            metadata=metadata,
            episodes=episodes,
        )
