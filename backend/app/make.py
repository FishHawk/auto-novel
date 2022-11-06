import logging
from pathlib import Path

from lxml import etree
from ebooklib import epub

from app.model import Book, TocChapterToken, TocEpisodeToken


_MISSING_EPISODE_HINT = "该章节缺失。"


def mix_texts(from_text: str, to_text: str) -> str:
    return f"{from_text}[{to_text}]"


def _epub_setup(
    epub_book: epub.EpubBook,
    book: Book,
):
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


def _epub_add_episode(
    epub_book: epub.EpubBook,
    token: TocEpisodeToken,
    body: etree.Element,
):
    uid = f"episode-{token.episode_id}"
    filename = f"{uid}.xhtml"

    content = etree.tostring(
        body,
        pretty_print=True,
        encoding="utf-8",
    )

    epub_html = epub.EpubHtml(
        uid=uid,
        title=token.title,
        file_name=filename,
        content=content,
    )

    epub_book.add_item(epub_html)
    epub_book.toc.append(epub.Link(filename, token.title, uid))
    epub_book.spine.append(epub_html)


def _epub_add_episodes(
    epub_book: epub.EpubBook,
    book: Book,
):
    for token in book.metadata.toc:
        if isinstance(token, TocChapterToken):
            epub_book.toc.append(epub.Section(token.title))
        elif isinstance(token, TocEpisodeToken):
            body = etree.Element("body")
            etree.SubElement(body, "h1").text = token.title

            episode = book.episodes.get(token.episode_id)
            if episode:
                for text in episode.paragraphs:
                    etree.SubElement(body, "p").text = text
            else:
                etree.SubElement(body, "p").text = _MISSING_EPISODE_HINT

            _epub_add_episode(
                epub_book=epub_book,
                token=token,
                body=body,
            )


def _epub_add_mixed_episodes(
    epub_book: epub.EpubBook,
    book: Book,
    secondary_book: Book,
):
    for token, secondary_token in zip(book.metadata.toc, secondary_book.metadata.toc):
        if isinstance(token, TocChapterToken):
            assert isinstance(secondary_token, TocChapterToken)
            mixed_title = mix_texts(token.title, secondary_token.title)
            epub_book.toc.append(epub.Section(mixed_title))
        elif isinstance(token, TocEpisodeToken):
            assert isinstance(secondary_token, TocEpisodeToken)
            body = etree.Element("body")
            etree.SubElement(body, "h1").text = token.title

            episode = book.episodes.get(token.episode_id)
            secondary_episode = secondary_book.episodes.get(token.episode_id)
            if episode and secondary_episode:
                etree.SubElement(
                    body, "p", {"style": "opacity:0.4;"}
                ).text = secondary_token.title
                for text, secondary_text in zip(
                    episode.paragraphs,
                    secondary_episode.paragraphs,
                ):
                    if text.strip():
                        etree.SubElement(body, "p").text = text.rstrip()
                        etree.SubElement(
                            body, "p", {"style": "opacity:0.4;"}
                        ).text = secondary_text.lstrip()
                    else:
                        etree.SubElement(body, "p").text = text
            elif episode:
                for text in episode.paragraphs:
                    etree.SubElement(body, "p").text = text
            else:
                etree.SubElement(body, "p").text = _MISSING_EPISODE_HINT

            _epub_add_episode(
                epub_book=epub_book,
                token=token,
                body=body,
            )


def make_epub(file_path: Path, book: Book):
    epub_book = epub.EpubBook()
    _epub_setup(epub_book, book)
    _epub_add_episodes(epub_book, book)
    epub.write_epub(file_path, epub_book, {})


def make_mixed_epub(file_path: Path, book: Book, secondary_book: Book):
    epub_book = epub.EpubBook()
    _epub_setup(epub_book, book)
    _epub_add_mixed_episodes(epub_book, book, secondary_book)
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
                    file.write(_MISSING_EPISODE_HINT)
                    file.write("\n")


def make_book(
    output_path: Path,
    book: Book,
    secondary_book: Book | None = None,
    epub_enabled: bool = True,
    epub_mixed_enabled: bool = True,
    txt_enabled: bool = True,
):
    file_path = f"{book.provider}.{book.book_id}.{book.lang}"

    if epub_enabled:
        epub_file_path = output_path / f"{file_path}.epub"
        logging.info("制作epub: %s", epub_file_path)
        make_epub(
            file_path=epub_file_path,
            book=book,
        )

    if epub_mixed_enabled and secondary_book:
        epub_file_path = output_path / f"{file_path}.mixed.epub"
        logging.info("制作混合epub: %s", epub_file_path)
        make_mixed_epub(
            file_path=epub_file_path,
            book=book,
            secondary_book=secondary_book,
        )

    if txt_enabled:
        txt_file_path = output_path / f"{file_path}.txt"
        logging.info("制作txt: %s", txt_file_path)
        make_txt(
            file_path=txt_file_path,
            book=book,
        )
