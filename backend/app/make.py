import logging
from pathlib import Path
from typing import List

from lxml import etree
from ebooklib import epub

from app.cache import BookCache
from app.model import Book, TocChapterToken, TocEpisodeToken
from app.provider.base import BookProvider
from app.translator import get_translator


_MISSING_EPISODE_HINT = {
    "en": "This episode is missing.",
    "zh": "该章节缺失。",
}


def _missing_episode_hint(lang: str) -> str:
    return _MISSING_EPISODE_HINT.get(lang, _MISSING_EPISODE_HINT["en"])


def make_epub(file_path: Path, book: Book):
    epub_book = epub.EpubBook()

    # set metadata
    epub_book.set_identifier(f"{book.provider}.{book.book_id}")
    epub_book.set_language(book.lang)
    epub_book.set_title(book.metadata.title)
    for author in book.metadata.authors:
        epub_book.add_author(author.name)
    epub_book.spine = ["nav"]

    # add default NCX and Nav file
    epub_book.add_item(epub.EpubNcx())
    epub_book.add_item(epub.EpubNav())

    # add CSS file
    style = "BODY {color: white;}"
    nav_css = epub.EpubItem(
        uid="style_nav",
        file_name="style/nav.css",
        media_type="text/css",
        content=style,
    )
    epub_book.add_item(nav_css)

    # add episodes
    for token in book.metadata.toc:
        if isinstance(token, TocChapterToken):
            epub_book.toc.append(epub.Section(token.title))
        elif isinstance(token, TocEpisodeToken):
            uid = f"episode-{token.episode_id}"
            filename = f"{uid}.xhtml"

            body = etree.Element("body")
            etree.SubElement(body, "h1").text = token.title

            episode = book.episodes.get(token.episode_id)
            if episode:
                for txt in episode.paragraphs:
                    etree.SubElement(body, "p").text = txt
            else:
                text = _missing_episode_hint(book.lang)
                etree.SubElement(body, "p").text = text

            content = etree.tostring(
                body,
                pretty_print=True,
                encoding="utf-8",
            )

            epub_html = epub.EpubHtml(
                uid=uid,
                title=token.title,
                file_name=filename,
                lang=book.lang,
                content=content,
            )

            epub_book.add_item(epub_html)
            epub_book.toc.append(epub.Link(filename, token.title, uid))
            epub_book.spine.append(epub_html)

    # save epub
    epub.write_epub(file_path, epub_book, {})


def make_txt(file_path: Path, book: Book):
    with open(file_path, "w", encoding="utf-8") as file:
        file.write(book.metadata.title)
        file.write("\n")

        for author in book.metadata.authors:
            file.write(f"{author.name}[{author.link}]")
        file.write("\n")

        if book.metadata.introduction.strip():
            file.write(book.metadata.introduction)
            file.write("\n")

        file.write("\n" * 3)

        for token in book.metadata.toc:
            if isinstance(token, TocChapterToken):
                file.write(f"# {token.title}\n\n")
            elif isinstance(token, TocEpisodeToken):
                file.write(f"# {token.title}\n\n")
                episode = book.episodes.get(token.episode_id)
                if episode:
                    for text in episode.paragraphs:
                        file.write(text)
                        file.write("\n")
                else:
                    text = _missing_episode_hint(book.lang)
                    file.write(text)
                    file.write("\n")


def make_book(
    output_path: Path,
    book: Book,
    epub_enabled: bool = True,
    txt_enabled: bool = True,
):
    file_path = f"{book.provider}.{book.book_id}.{book.lang}"

    if epub_enabled:
        epub_file_path = output_path / f"{file_path}.epub"
        logging.info("制作epub: %s", epub_file_path)
        make_epub(file_path=epub_file_path, book=book)

    if txt_enabled:
        txt_file_path = output_path / f"{file_path}.txt"
        logging.info("制作txt: %s", txt_file_path)
        make_txt(file_path=txt_file_path, book=book)


def get_and_make_book(
    provider: BookProvider,
    book_id: str,
    cache: BookCache | None,
    output_path: Path,
    langs: List[str],
    epub_enabled: bool = True,
    txt_enabled: bool = True,
):
    book = provider.get_book(
        book_id=book_id,
        cache=cache,
    )

    make_book(
        output_path=output_path,
        book=book,
        epub_enabled=epub_enabled,
        txt_enabled=txt_enabled,
    )

    translator = get_translator("baidu")

    for lang in langs:
        if lang == book.lang:
            continue

        book_translated = translator.translate_book(
            book=book,
            lang=lang,
            cache=cache,
        )

        make_book(
            output_path=output_path,
            book=book_translated,
            epub_enabled=epub_enabled,
            txt_enabled=txt_enabled,
        )
