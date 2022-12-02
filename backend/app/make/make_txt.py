from pathlib import Path

from app.model import Book, TocChapterToken, TocEpisodeToken

_MISSING_EPISODE_HINT = "该章节缺失。"


def _write_meta(file, book: Book):
    file.write(book.metadata.title)
    file.write("\n")

    for author in book.metadata.authors:
        file.write(f"{author.name}[{author.link}]")
    file.write("\n")

    if book.metadata.introduction.strip():
        file.write(book.metadata.introduction)
        file.write("\n")

    file.write("\n" * 3)


def make_txt_file(
    file_path: Path,
    book: Book,
):
    with open(file_path, "w", encoding="utf-8") as file:
        _write_meta(file, book)

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

                file.write("\n\n\n")


def make_mixed_txt_file(
    file_path: Path,
    book: Book,
    secondary_book: Book,
):
    with open(file_path, "w", encoding="utf-8") as file:
        _write_meta(file, book)

        for token, secondary_token in zip(
            book.metadata.toc,
            secondary_book.metadata.toc,
        ):
            if isinstance(token, TocChapterToken):
                assert isinstance(secondary_token, TocChapterToken)
                file.write(f"# {token.title}\n")
                file.write(f"# {secondary_token.title}\n\n")
            elif isinstance(token, TocEpisodeToken):
                assert isinstance(secondary_token, TocEpisodeToken)
                file.write(f"# {token.title}\n")
                file.write(f"# {secondary_token.title}\n\n")

                episode = book.episodes.get(token.episode_id)
                secondary_episode = secondary_book.episodes.get(token.episode_id)
                if episode and secondary_episode:
                    for text, secondary_text in zip(
                        episode.paragraphs,
                        secondary_episode.paragraphs,
                    ):
                        if text.strip():
                            file.write(text.rstrip() + "\n")
                            file.write(secondary_text.lstrip() + "\n\n")
                        else:
                            file.write(text + "\n")
                elif episode:
                    for text in episode.paragraphs:
                        file.write(text + "\n")
                else:
                    file.write(_MISSING_EPISODE_HINT + "\n")

                file.write("\n\n\n")
