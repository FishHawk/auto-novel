import logging
from pathlib import Path

from app.model import Book
from app.make.make_epub import make_epub, make_mixed_epub
from app.make.make_txt import make_txt, make_mixed_txt


def make_book(
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

    if txt_mixed_enabled and secondary_book:
        epub_file_path = output_path / f"{file_path}.mixed.txt"
        logging.info("制作混合txt: %s", epub_file_path)
        make_mixed_txt(
            file_path=epub_file_path,
            book=book,
            secondary_book=secondary_book,
        )
