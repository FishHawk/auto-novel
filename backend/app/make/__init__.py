import logging
from pathlib import Path

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
    book_type: str,
    cache_dir: Path,
):
    cache = BookCache(
        cache_dir=cache_dir,
        provider_id=provider_id,
        book_id=book_id,
    )

    logging.info("获取元数据:%s/%s", provider_id, book_id)
    metadata = cache.get_book_metadata("jp")
    assert metadata

    if lang == "zh":
        cache.metadata_max_age = 60000
        logging.info("翻译元数据:%s/%s", provider_id, book_id)
        translated_metadata = cache.get_book_metadata("zh")
        assert translated_metadata

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
            episode = cache.get_episode("jp", episode_id)
            if not episode:
                logging.info("跳过缺失章节:%s/%s/%s", provider_id, book_id, episode_id)
            episodes[episode_id] = episode
        except Exception as exception:
            logging.warning("获取章节失败:%s/%s/%s", provider_id, book_id, episode_id)
            logging.warning(exception, exc_info=True)

        if lang == "zh" and episode_id in episodes:
            logging.info(
                "翻译章节:%d/%d %s/%s/%s",
                index + 1,
                len(episode_ids),
                provider_id,
                book_id,
                episode_id,
            )
            try:
                translated_episode = cache.get_episode("zh", episode_id)
                if not translated_episode:
                    logging.info("跳过缺失章节:%s/%s/%s", provider_id, book_id, episode_id)
                translated_episodes[episode_id] = translated_episode
            except Exception as exception:
                logging.warning("翻译章节失败:%s/%s/%s", provider_id, book_id, episode_id)
                logging.warning(exception, exc_info=True)

    book = Book(
        book_id=book_id,
        provider_id=provider_id,
        lang="jp",
        metadata=metadata,
        episodes=episodes,
    )

    if lang == "jp":
        _make_files(
            output_path=cache_dir,
            book=book,
            epub_enabled=book_type == "epub",
            epub_mixed_enabled=book_type == "mixed.epub",
            txt_enabled=book_type == "txt",
            txt_mixed_enabled=book_type == "mixed.txt",
        )

    if lang == "zh":
        translated_book = Book(
            book_id=book_id,
            provider_id=provider_id,
            lang="zh",
            metadata=translated_metadata,
            episodes=translated_episodes,
        )
        _make_files(
            output_path=cache_dir,
            book=translated_book,
            secondary_book=book,
            epub_enabled=book_type == "epub",
            epub_mixed_enabled=book_type == "mixed.epub",
            txt_enabled=book_type == "txt",
            txt_mixed_enabled=book_type == "mixed.txt",
        )
