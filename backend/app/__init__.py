import logging
import os
from pathlib import Path

from flask import Flask, request
import redis
import rq

from app.provider import get_provider, parse_url_as_provider_and_book_id
from app.translator import get_default_translator
from app.cache import BookCache
from app.model import TocEpisodeToken
from app.make import make_book

redis_connection = redis.Redis(host="redis", port=6379, db=0)
queue = rq.Queue(connection=redis_connection)

books_path = Path("/books")


def get_status(job_id: str) -> str | None:
    job = queue.fetch_job(job_id=job_id)
    if not job:
        return None

    status = job.get_status(refresh=True)
    if status in ["queued", "started", "failed"]:
        return status
    else:
        return "unknown"


def update_book(provider_id: str, book_id: str, lang: str):
    provider = get_provider(provider_id)

    cache = BookCache(
        cache_path=books_path / f"{provider_id}.{book_id}.zip",
    )

    book = provider.get_book(
        book_id=book_id,
        cache=cache,
    )

    make_book(
        output_path=books_path,
        book=book,
    )

    if lang != provider.lang:
        translator = get_default_translator()

        book_translated = translator.translate_book(
            book=book,
            lang=lang,
            cache=cache,
        )

        make_book(
            output_path=books_path,
            book=book_translated,
        )


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

        metadata = provider.get_book_metadata(
            book_id=book_id,
            cache=cache,
        )

        total_episode_number = sum(
            [1 for token in metadata.toc if isinstance(token, TocEpisodeToken)]
        )

        book_file_groups = []
        for lang in ["jp", "zh"]:
            if cache:
                cached_episode_number = cache.count_episode(lang)
            else:
                cached_episode_number = 0

            status = get_status(job_id="/".join([provider.provider_id, book_id, lang]))

            book_file_group = {
                "lang": lang,
                "status": status,
                "total_episode_number": total_episode_number,
                "cached_episode_number": cached_episode_number,
                "files": [],
            }

            for book_type in ["epub", "txt"]:
                book_name = f"{provider.provider_id}.{book_id}.{lang}.{book_type}"
                book_path = books_path / book_name
                if not book_path.exists:
                    book_name = None
                book_file_group["files"].append(
                    {"type": book_type, "filename": book_name}
                )

            book_file_groups.append(book_file_group)

        return {
            "provider_id": provider.provider_id,
            "book_id": book_id,
            "title": metadata.title,
            "files": book_file_groups,
        }

    @app.post("/book-update")
    def _create_book_update_job():
        provider_id = request.json["provider_id"]
        if not provider_id:
            return "没有provider_id参数", 400

        book_id = request.json.get("book_id")
        if not book_id:
            return "没有book_id参数", 400

        lang = request.json.get("lang")
        if not lang:
            return "没有lang参数", 400

        job_id = "/".join([provider_id, book_id, lang])

        if queue.fetch_job(job_id=job_id):
            return "更新任务已经在队列中", 500

        queue.enqueue(
            update_book,
            provider_id,
            book_id,
            lang,
            result_ttl=0,
            failure_ttl=5 * 60,
            job_id=job_id,
        )
        return "成功添加更新任务"

    return app
