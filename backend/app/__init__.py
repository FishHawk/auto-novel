import itertools
import logging
from pathlib import Path
from typing import Dict, List

from flask import Flask, request
import redis
import rq

from app.provider import build_url, get_provider, parse_url_as_provider_and_book_id
from app.translator import get_translator, DEFAULT_TRANSLATOR_ID
from app.cache import BookCache
from app.model import BookMetadata, TocEpisodeToken
from app.make import make_book

logging.basicConfig(
    level=logging.INFO,
)

redis_connection = redis.Redis(host="redis", port=6379, db=0)
queue = rq.Queue(connection=redis_connection)

books_path = Path("/books")


def update_book(
    provider_id: str,
    book_id: str,
    lang: str,
    start_index: int,
    end_index: int,
):
    provider = get_provider(provider_id)

    cache = BookCache(
        cache_path=books_path / f"{provider_id}.{book_id}.zip",
    )

    book = provider.get_book(
        book_id=book_id,
        cache=cache,
        allow_request=lambda i: start_index <= i <= end_index,
    )

    make_book(
        output_path=books_path,
        book=book,
    )

    if lang != provider.lang:
        translator = get_translator(
            DEFAULT_TRANSLATOR_ID,
            from_lang=book.lang,
            to_lang=lang,
        )

        book_translated = translator.translate_book(
            book=book,
            cache=cache,
            allow_request=lambda i: start_index <= i <= end_index,
        )

        make_book(
            output_path=books_path,
            book=book_translated,
            secondary_book=book,
        )


def get_status(job_id: str) -> str | None:
    job = queue.fetch_job(job_id=job_id)
    if not job:
        return None

    status = job.get_status(refresh=True)
    if status in ["queued", "started", "failed"]:
        return status
    else:
        return "unknown"


def get_book(
    url: str,
    provider_id: str,
    book_id: str,
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
            "status": None,
            "total_episode_number": total_episode_number,
            "cached_episode_number": cached_episode_number,
            "files": [],
            "mixed_files": [],
        }

        possible_book_types = ["txt", "epub"]

        for book_type in possible_book_types:
            book_name = f"{provider_id}.{book_id}.{lang}.{book_type}"
            if not (books_path / book_name).exists():
                book_name = None
            book_file_group["files"].append({"type": book_type, "filename": book_name})

            if lang != "jp":
                mixed_book_name = f"{provider_id}.{book_id}.{lang}.mixed.{book_type}"
                if not (books_path / mixed_book_name).exists():
                    mixed_book_name = None
                book_file_group["mixed_files"].append(
                    {"type": book_type, "filename": mixed_book_name}
                )

        book_file_groups.append(book_file_group)

    return {
        "url": url,
        "provider_id": provider_id,
        "book_id": book_id,
        "title": metadata.title,
        "files": book_file_groups,
    }


def create_app():
    app = Flask(__name__)

    @app.get("/query")
    def _query():
        url = request.args.get("url")
        if url is None:
            return "没有url参数", 400

        parsed = parse_url_as_provider_and_book_id(url)
        if parsed is None:
            logging.info("无法解析网址: %s", url)
            return "无法解析网址，可能是因为格式错误或者不支持", 400

        provider, book_id = parsed

        cache = BookCache(
            cache_path=books_path / f"{provider.provider_id}.{book_id}.zip",
        )

        try:
            metadata = provider.get_book_metadata(
                book_id=book_id,
                cache=cache,
            )
        except Exception:
            return "获取元数据失败。", 500

        book = get_book(
            url=provider.build_url_from_book_id(book_id=book_id),
            provider_id=provider.provider_id,
            book_id=book_id,
            metadata=metadata,
            cache=cache,
        )

        for group in book["files"]:
            group["status"] = get_status(
                job_id="/".join([provider.provider_id, book_id, group["lang"]])
            )

        return book

    @app.get("/list")
    def _list():
        page = request.args.get("page", 1, int)
        if page < 1:
            page = 1
        page_size = 10

        all_zip_paths = sorted(
            books_path.glob("*.zip"),
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

            cache = BookCache(zip_path)
            cache.metadata_max_age = 65536
            metadata = cache.get_book_metadata("jp")

            if not metadata:
                continue

            book = get_book(
                url=build_url(provider_id=provider_id, book_id=book_id),
                provider_id=provider_id,
                book_id=book_id,
                metadata=metadata,
                cache=cache,
            )

            books.append(book)

        return {
            "total": total,
            "books": books,
        }

    @app.post("/book-update/<provider_id>/<book_id>/<lang>")
    def _create_book_update_job(
        provider_id: str,
        book_id: str,
        lang: str,
    ):
        start_index = request.args.get("start_index", 0, int)
        end_index = request.args.get("end_index", 65536, int)

        if queue.count >= 100:
            return "更新任务数目已达上限100", 500

        job_id = "/".join([provider_id, book_id, lang])

        if queue.fetch_job(job_id=job_id):
            return "更新任务已经在队列中", 500

        queue.enqueue(
            update_book,
            provider_id,
            book_id,
            lang,
            start_index,
            end_index,
            job_timeout="10m",
            result_ttl=0,
            failure_ttl=0,
            job_id=job_id,
        )

        return "成功添加更新任务"

    route_boost(app)

    return app


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
            cache_path=books_path / f"{provider.provider_id}.{book_id}.zip",
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
            cache_path=books_path / f"{provider.provider_id}.{book_id}.zip",
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
            cache_path=books_path / f"{provider.provider_id}.{book_id}.zip",
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
            cache_path=books_path / f"{provider.provider_id}.{book_id}.zip",
        )

        translated_episode = cache.get_episode(
            episode_id=episode_id,
            lang="zh",
        )
        if translated_episode:
            return "已经存在"

        episode = cache.get_episode(
            episode_id=episode_id,
            lang=provider.lang,
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

    @app.post("/boost/make/<provider_id>/<book_id>")
    def make(
        provider_id: str,
        book_id: str,
    ):
        provider = get_provider(provider_id)
        if not provider:
            return "找不到provider", 404

        cache = BookCache(
            cache_path=books_path / f"{provider_id}.{book_id}.zip",
        )

        book = provider.get_book(
            book_id=book_id,
            cache=cache,
            allow_request=lambda _: False,
        )
        make_book(
            output_path=books_path,
            book=book,
        )

        translator = get_translator(
            DEFAULT_TRANSLATOR_ID,
            from_lang=book.lang,
            to_lang="zh",
        )

        book_translated = translator.translate_book(
            book=book,
            cache=cache,
            allow_request=lambda i: False,
        )
        make_book(
            output_path=books_path,
            book=book_translated,
            secondary_book=book,
        )

        return "成功"
