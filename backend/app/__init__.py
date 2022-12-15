import logging
from pathlib import Path

from flask import Flask, request
import redis
import rq

from app.provider import get_provider
from app.make import make_book
from app.cache import BookCache
from app.model import BookMetadata, TocEpisodeToken

logging.basicConfig(
    level=logging.INFO,
)

BOOKS_DIR = Path("/books")


def create_app():
    app = Flask(__name__)
    route_base(app)
    route_content(app)
    route_boost(app)
    return app


def get_book_file_groups(
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
            if not (BOOKS_DIR / book_name).exists():
                book_name = None
            book_file_group["files"].append({"type": book_type, "filename": book_name})

            if lang != "jp":
                mixed_book_name = f"{provider_id}.{book_id}.{lang}.mixed.{book_type}"
                if not (BOOKS_DIR / mixed_book_name).exists():
                    mixed_book_name = None
                book_file_group["mixed_files"].append(
                    {"type": book_type, "filename": mixed_book_name}
                )

        book_file_groups.append(book_file_group)

    return book_file_groups


def route_base(app: Flask):
    redis_connection = redis.Redis(host="redis", port=6379, db=0)
    queue = rq.Queue(connection=redis_connection)

    def get_status(job_id: str) -> str | None:
        job = queue.fetch_job(job_id=job_id)
        if not job:
            return None

        status = job.get_status(refresh=True)
        if status in ["queued", "started", "failed"]:
            return status
        else:
            return "unknown"

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
        except Exception as e:
            return "获取元数据失败。", 500

        book_file_groups = get_book_file_groups(
            provider_id=provider_id,
            book_id=book_id,
            metadata=metadata,
            cache=cache,
        )
        for group in book_file_groups:
            group["status"] = get_status(
                job_id="/".join([provider.provider_id, book_id, group["lang"]])
            )

        return book_file_groups

    @app.post("/storage/<provider_id>/<book_id>/<lang>")
    def create_storage_update_job(
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
            make_book,
            provider_id,
            book_id,
            lang,
            start_index,
            end_index,
            BOOKS_DIR,
            job_timeout="10m",
            result_ttl=0,
            failure_ttl=0,
            job_id=job_id,
        )

        return "成功添加更新任务"

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
                provider_id=provider_id,
                book_id=book_id,
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
        make_book(
            provider_id=provider_id,
            book_id=book_id,
            lang="zh",
            start_index=-1,
            end_index=-1,
            cache_dir=BOOKS_DIR,
        )
        return "成功"
