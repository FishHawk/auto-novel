import logging
from pathlib import Path

from app.provider import get_provider
from app.translator import get_translator, DEFAULT_TRANSLATOR_ID
from app.cache import BookCache
from app.model import Book, TocEpisodeToken
from app.make.make_epub import make_epub_file, make_mixed_epub_file
from app.make.make_txt import make_txt_file, make_mixed_txt_file


def _make_files(
    output_path: Path,
    book: Book,
    secondary_book: Book | None = None,
    epub_enabled: bool = True,
    epub_mixed_enabled: bool = True,
    txt_enabled: bool = True,
    txt_mixed_enabled: bool = True,
):
    file_path = f"{book.provider_id}.{book.book_id}.{book.lang}"

    if epub_enabled:
        epub_file_path = output_path / f"{file_path}.epub"
        logging.info("制作epub: %s", epub_file_path)
        make_epub_file(
            file_path=epub_file_path,
            book=book,
        )

    if epub_mixed_enabled and secondary_book:
        epub_file_path = output_path / f"{file_path}.mixed.epub"
        logging.info("制作混合epub: %s", epub_file_path)
        make_mixed_epub_file(
            file_path=epub_file_path,
            book=book,
            secondary_book=secondary_book,
        )

    if txt_enabled:
        txt_file_path = output_path / f"{file_path}.txt"
        logging.info("制作txt: %s", txt_file_path)
        make_txt_file(
            file_path=txt_file_path,
            book=book,
        )

    if txt_mixed_enabled and secondary_book:
        epub_file_path = output_path / f"{file_path}.mixed.txt"
        logging.info("制作混合txt: %s", epub_file_path)
        make_mixed_txt_file(
            file_path=epub_file_path,
            book=book,
            secondary_book=secondary_book,
        )


def make_book(
    provider_id: str,
    book_id: str,
    lang: str,
    start_index: int,
    end_index: int,
    cache_dir: Path,
    epub_enabled: bool = True,
    epub_mixed_enabled: bool = True,
    txt_enabled: bool = True,
    txt_mixed_enabled: bool = True,
):
    provider = get_provider(provider_id)

    translator = None
    if lang != provider.lang:
        translator = get_translator(
            DEFAULT_TRANSLATOR_ID,
            from_lang=provider.lang,
            to_lang=lang,
        )

    cache = BookCache(
        cache_dir=cache_dir,
        provider_id=provider_id,
        book_id=book_id,
    )

    logging.info("获取元数据:%s/%s", provider_id, book_id)
    metadata = provider.get_book_metadata(
        book_id=book_id,
        cache=cache,
    )

    if translator:
        logging.info("翻译元数据:%s/%s", provider_id, book_id)
        translated_metadata = translator.translate_metadata(
            metadata=metadata,
            cache=cache,
        )

    episode_ids = [
        token.episode_id for token in metadata.toc if isinstance(token, TocEpisodeToken)
    ]

    episodes = {}
    translated_episodes = {}
    for index, episode_id in enumerate(episode_ids):
        logging.info(
            "获取章节:%d/%d %s/%s/%s",
            index + 1,
            len(episode_ids),
            provider_id,
            book_id,
            episode_id,
        )
        try:
            episode = provider.get_episode(
                book_id=book_id,
                episode_id=episode_id,
                cache=cache,
                allow_request=(start_index <= index <= end_index),
            )
            if not episode:
                logging.info("跳过缺失章节:%s/%s/%s", provider_id, book_id, episode_id)
            episodes[episode_id] = episode
        except Exception as exception:
            logging.warning("获取章节失败:%s/%s/%s", provider_id, book_id, episode_id)
            logging.warning(exception, exc_info=True)

        if translator and episode_id in episodes:
            logging.info(
                "翻译章节:%d/%d %s/%s/%s",
                index + 1,
                len(episode_ids),
                provider_id,
                book_id,
                episode_id,
            )
            try:
                translated_episode = translator.translate_episode(
                    episode_id=episode_id,
                    episode=episodes[episode_id],
                    cache=cache,
                    allow_request=(start_index <= index <= end_index),
                )
                if not translated_episode:
                    logging.info("跳过缺失章节:%s/%s/%s", provider_id, book_id, episode_id)
                translated_episodes[episode_id] = translated_episode
            except Exception as exception:
                logging.warning("翻译章节失败:%s/%s/%s", provider_id, book_id, episode_id)
                logging.warning(exception, exc_info=True)

    book = Book(
        book_id=book_id,
        provider_id=provider_id,
        lang=provider.lang,
        metadata=metadata,
        episodes=episodes,
    )
    _make_files(
        output_path=cache_dir,
        book=book,
        epub_enabled=epub_enabled,
        epub_mixed_enabled=epub_mixed_enabled,
        txt_enabled=txt_enabled,
        txt_mixed_enabled=txt_mixed_enabled,
    )

    if translator:
        translated_book = Book(
            book_id=book_id,
            provider_id=provider_id,
            lang=lang,
            metadata=translated_metadata,
            episodes=translated_episodes,
        )
        _make_files(
            output_path=cache_dir,
            book=translated_book,
            secondary_book=book,
            epub_enabled=epub_enabled,
            epub_mixed_enabled=epub_mixed_enabled,
            txt_enabled=txt_enabled,
            txt_mixed_enabled=txt_mixed_enabled,
        )
