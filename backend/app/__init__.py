import logging
from pathlib import Path

from flask import Flask, request, redirect, url_for
from app.provider import get_provider
from app.make import make_book
from app.cache import BookCache
from app.model import BookMetadata, TocEpisodeToken
from app.translator import get_default_translator

logging.basicConfig(
    level=logging.INFO,
)

BOOKS_DIR = Path("/books")


def create_app():
    app = Flask(__name__)
    route_base(app)
    route_content(app)
    route_update(app)
    route_boost(app)
    route_make(app)
    return app


def get_book_file_groups(
    metadata: BookMetadata,
    cache: BookCache,
):
    total_episode_number = sum(
        [1 for token in metadata.toc if isinstance(token, TocEpisodeToken)]
    )

    book_file_groups = []
    for lang in ["jp", "zh"]:
        cached_episode_number = cache.count_episode(lang)

        book_file_group = {
            "lang": lang,
            "total": total_episode_number,
            "cached": cached_episode_number,
        }
        book_file_groups.append(book_file_group)

    return book_file_groups


def route_base(app: Flask):
    @app.get("/storage/<provider_id>/<book_id>")
    def get_storage_state(
        provider_id: str,
        book_id: str,
    ):
        provider = get_provider(provider_id)
        if not provider:
            return "找不到provider", 404

        cache = BookCache(
            cache_dir=BOOKS_DIR,
            provider_id=provider_id,
            book_id=book_id,
        )

        try:
            metadata = provider.get_book_metadata(
                book_id=book_id,
                cache=cache,
            )
        except Exception:
            return "获取元数据失败。", 500

        book_file_groups = get_book_file_groups(
            metadata=metadata,
            cache=cache,
        )
        return book_file_groups

    @app.get("/storage-list")
    def _list():
        page = request.args.get("page", 1, int)
        if page < 1:
            page = 1
        page_size = 10

        all_zip_paths = sorted(
            BOOKS_DIR.glob("*.zip"),
            key=lambda p: p.stat().st_mtime,
            reverse=True,
        )
        total = len(all_zip_paths)

        paged_zip_paths = all_zip_paths[page_size * (page - 1) : page_size * page]

        books = []
        for zip_path in paged_zip_paths:
            try:
                provider_id, book_id = zip_path.stem.split(".")
            except ValueError:
                continue

            cache = BookCache(BOOKS_DIR, provider_id, book_id)
            cache.metadata_max_age = 65536
            metadata = cache.get_book_metadata("jp")

            if not metadata:
                continue

            book_file_groups = get_book_file_groups(
                metadata=metadata,
                cache=cache,
            )
            book = {
                "provider_id": provider_id,
                "book_id": book_id,
                "title": metadata.title,
                "files": book_file_groups,
            }

            books.append(book)

        return {
            "total": total,
            "books": books,
        }


def route_content(app: Flask):
    @app.get("/content/metadata/<provider_id>/<book_id>")
    def get_content_metadata(
        provider_id: str,
        book_id: str,
    ):
        provider = get_provider(provider_id)
        if not provider:
            return "找不到provider", 404

        cache = BookCache(
            cache_dir=BOOKS_DIR,
            provider_id=provider_id,
            book_id=book_id,
        )

        try:
            metadata = provider.get_book_metadata(
                book_id=book_id,
                cache=cache,
            )
        except Exception:
            return "获取元数据失败。", 500

        cache.metadata_max_age = 65536
        return {
            "jp": metadata,
            "zh": cache.get_book_metadata(lang="zh"),
        }

    @app.get("/content/episode/<provider_id>/<book_id>/<episode_id>")
    def get_content_episode(
        provider_id: str,
        book_id: str,
        episode_id: str,
    ):
        provider = get_provider(provider_id)
        if not provider:
            return "找不到provider", 404

        cache = BookCache(
            cache_dir=BOOKS_DIR,
            provider_id=provider_id,
            book_id=book_id,
        )

        try:
            metadata = provider.get_book_metadata(
                book_id=book_id,
                cache=cache,
            )
        except Exception:
            return "获取元数据失败。", 500

        episode = provider.get_episode(
            book_id=book_id,
            episode_id=episode_id,
            cache=cache,
            allow_request=True,
        )

        tokens = [token for token in metadata.toc if isinstance(token, TocEpisodeToken)]
        index = next(i for i, v in enumerate(tokens) if v.episode_id == episode_id)
        curr_token = tokens[index]
        prev_token = tokens[index - 1] if index > 0 else None
        next_token = tokens[index + 1] if index < len(tokens) - 1 else None

        cache.metadata_max_age = 65536
        metadata_zh = cache.get_book_metadata(lang="zh")
        if metadata_zh:
            for token in [
                token for token in metadata_zh.toc if isinstance(token, TocEpisodeToken)
            ]:
                if curr_token and token.episode_id == curr_token.episode_id:
                    curr_token = {
                        "episode_id": token.episode_id,
                        "title": curr_token.title,
                        "zh_title": token.title,
                    }
                    break
            for token in [
                token for token in metadata_zh.toc if isinstance(token, TocEpisodeToken)
            ]:
                if prev_token and token.episode_id == prev_token.episode_id:
                    prev_token = {
                        "episode_id": token.episode_id,
                        "title": prev_token.title,
                        "zh_title": token.title,
                    }
                    break
            for token in [
                token for token in metadata_zh.toc if isinstance(token, TocEpisodeToken)
            ]:
                if next_token and token.episode_id == next_token.episode_id:
                    next_token = {
                        "episode_id": token.episode_id,
                        "title": next_token.title,
                        "zh_title": token.title,
                    }
                    break
        episode_zh = cache.get_episode(lang="zh", episode_id=episode_id)
        paragraphs_zh = None
        if episode_zh:
            paragraphs_zh = episode_zh.paragraphs

        return {
            "curr": curr_token,
            "prev": prev_token,
            "next": next_token,
            "jp": episode.paragraphs,
            "zh": paragraphs_zh,
        }


def route_update(app: Flask):
    @app.post("/update/metadata/<provider_id>/<book_id>")
    def post_metadata_update_task(
        provider_id: str,
        book_id: str,
    ):
        start_index = request.args.get("startIndex", 0, int)
        end_index = request.args.get("endIndex", 65536, int)
        translated = request.args.get(
            "translated", False, type=lambda v: v.lower() == "true"
        )

        provider = get_provider(provider_id)
        if not provider:
            return "找不到provider", 404

        cache = BookCache(
            cache_dir=BOOKS_DIR,
            provider_id=provider_id,
            book_id=book_id,
        )

        try:
            metadata = provider.get_book_metadata(
                book_id=book_id,
                cache=cache,
            )
        except Exception:
            return "获取元数据失败。", 500

        try:
            if translated:
                translator = get_default_translator()
                translator.translate_metadata(
                    metadata=metadata,
                    cache=cache,
                )
        except Exception:
            return "翻译失败。", 500

        episode_ids = [
            token.episode_id
            for token in metadata.toc
            if isinstance(token, TocEpisodeToken)
        ][start_index:end_index]

        lang = "zh" if translated else "jp"
        uncached_episode_ids = list(
            filter(
                lambda episode_id: cache.get_episode(lang, episode_id) is None,
                episode_ids,
            )
        )

        print(uncached_episode_ids)
        return uncached_episode_ids

    @app.post("/update/episode/<provider_id>/<book_id>/<episode_id>")
    def post_episode_update_task(
        provider_id: str,
        book_id: str,
        episode_id: str,
    ):
        print(episode_id)
        translated = request.args.get("translated", False, bool)

        provider = get_provider(provider_id)
        if not provider:
            return "找不到provider", 404

        cache = BookCache(
            cache_dir=BOOKS_DIR,
            provider_id=provider_id,
            book_id=book_id,
        )
        episode = provider.get_episode(
            book_id=book_id,
            episode_id=episode_id,
            cache=cache,
            allow_request=True,
        )

        try:
            if translated:
                translator = get_default_translator()
                translator.translate_episode(
                    episode_id=episode_id,
                    episode=episode,
                    cache=cache,
                    allow_request=True,
                )
        except Exception:
            return "翻译失败。", 500

        return "成功"


def route_boost(app: Flask):
    @app.get("/boost/metadata/<provider_id>/<book_id>")
    def get_metadata(
        provider_id: str,
        book_id: str,
    ):
        start_index = request.args.get("start_index", 0, int)
        end_index = request.args.get("end_index", 65536, int)

        provider = get_provider(provider_id)
        if not provider:
            return "找不到provider", 404

        cache = BookCache(
            cache_dir=BOOKS_DIR,
            provider_id=provider_id,
            book_id=book_id,
        )

        try:
            metadata = provider.get_book_metadata(
                book_id=book_id,
                cache=cache,
            )
        except Exception:
            return "获取元数据失败。", 500

        episode_ids = [
            token.episode_id
            for token in metadata.toc
            if isinstance(token, TocEpisodeToken)
        ][start_index:end_index]

        uncached_episode_ids = list(
            filter(
                lambda episode_id: cache.get_episode("zh", episode_id) is None,
                episode_ids,
            )
        )

        return {
            "metadata": metadata.to_query_list(),
            "episode_ids": uncached_episode_ids,
        }

    @app.post("/boost/metadata/<provider_id>/<book_id>")
    def post_metadata(
        provider_id: str,
        book_id: str,
    ):
        provider = get_provider(provider_id)
        if not provider:
            return "找不到provider", 404

        cache = BookCache(
            cache_dir=BOOKS_DIR,
            provider_id=provider_id,
            book_id=book_id,
        )
        metadata = provider.get_book_metadata(
            book_id=book_id,
            cache=cache,
        )

        query_list = metadata.to_query_list()
        result_list = request.json
        assert all(isinstance(s, str) for s in result_list)
        assert len(query_list) == len(result_list)
        metadata.apply_translated_result(result_list)

        cache.save_book_metadata(
            lang="zh",
            metadata=metadata,
        )
        return "成功"

    @app.get("/boost/episode/<provider_id>/<book_id>/<episode_id>")
    def get_episode(
        provider_id: str,
        book_id: str,
        episode_id: str,
    ):
        provider = get_provider(provider_id)
        if not provider:
            return "找不到provider", 404

        cache = BookCache(
            cache_dir=BOOKS_DIR,
            provider_id=provider_id,
            book_id=book_id,
        )
        episode = provider.get_episode(
            book_id=book_id,
            episode_id=episode_id,
            cache=cache,
            allow_request=True,
        )
        return episode.paragraphs

    @app.post("/boost/episode/<provider_id>/<book_id>/<episode_id>")
    def post_episode(
        provider_id: str,
        book_id: str,
        episode_id: str,
    ):
        provider = get_provider(provider_id)
        if not provider:
            return "找不到provider", 404

        cache = BookCache(
            cache_dir=BOOKS_DIR,
            provider_id=provider_id,
            book_id=book_id,
        )

        translated_episode = cache.get_episode(
            episode_id=episode_id,
            lang="zh",
        )
        if translated_episode:
            return "已经存在"

        episode = cache.get_episode(
            episode_id=episode_id,
            lang="jp",
        )
        if not episode:
            return "不合法的episode_id", 400

        query_list = episode.paragraphs
        result_list = request.json
        assert all(isinstance(s, str) for s in result_list)
        assert len(query_list) == len(result_list)
        episode.paragraphs = result_list

        cache.save_episode(
            lang="zh",
            episode_id=episode_id,
            episode=episode,
        )
        return "成功"


def route_make(app):
    @app.get("/books/<provider_id>/<book_id>/<lang>/<book_type>")
    def generate_book_before_get(
        provider_id: str,
        book_id: str,
        lang: str,
        book_type: str,
    ):
        make_book(
            provider_id=provider_id,
            book_id=book_id,
            lang=lang,
            book_type=book_type,
            cache_dir=BOOKS_DIR,
        )
        filename = f"{provider_id}.{book_id}.{lang}.{book_type}"
        return redirect(f"../../../../../books/{filename}")
