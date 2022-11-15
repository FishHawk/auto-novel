from pathlib import Path
import pickle
import logging
from datetime import datetime
from zipfile import ZipFile, ZIP_BZIP2, ZipInfo

from app.model import BookMetadata, Episode


def _get_info(cache_path: Path, file_name: str) -> ZipInfo:
    try:
        if not cache_path.exists():
            return None
        with ZipFile(cache_path, "r", compression=ZIP_BZIP2) as cache_file:
            return cache_file.getinfo(file_name)
    except Exception:
        return None


def _read_cache(cache_path: Path, file_name: str):
    try:
        if not cache_path.exists():
            return None
        with ZipFile(cache_path, "r", compression=ZIP_BZIP2) as cache_file:
            pickled = cache_file.read(file_name)
        return pickle.loads(pickled)
    except Exception:
        return None


def _save_cache(cache_path: Path, file_name: str, data: any):
    try:
        if not cache_path.parent.exists():
            cache_path.parent.mkdir(parents=True, exist_ok=True)
        pickled = pickle.dumps(data, pickle.HIGHEST_PROTOCOL)
        with ZipFile(cache_path, "a", compression=ZIP_BZIP2) as cache_file:
            cache_file.writestr(file_name, pickled)
    except Exception as exception:
        logging.warning(
            "can not write cache: %s:%s, because %s",
            cache_path,
            file_name,
            exception,
        )


class BookCache:
    metadata_max_age = 2  # 元数据缓存有效时间，单位为天

    def __init__(self, cache_path: Path) -> None:
        self.cache_path = cache_path

    def _get_book_metadata_mtime(self, lang: str) -> datetime | None:
        info = _get_info(
            cache_path=self.cache_path,
            file_name=f"metadata.{lang}.pickle",
        )
        if not info:
            return None
        date_tuple = info.date_time
        return datetime(
            year=date_tuple[0],
            month=date_tuple[1],
            day=date_tuple[2],
            hour=date_tuple[3],
            minute=date_tuple[4],
            second=date_tuple[5],
        )

    def get_book_metadata(self, lang: str) -> BookMetadata | None:
        mtime = self._get_book_metadata_mtime(lang)
        if not mtime or (datetime.now() - mtime).days >= self.metadata_max_age:
            return None
        return _read_cache(
            cache_path=self.cache_path,
            file_name=f"metadata.{lang}.pickle",
        )

    def save_book_metadata(self, lang: str, metadata: BookMetadata):
        _save_cache(
            cache_path=self.cache_path,
            file_name=f"metadata.{lang}.pickle",
            data=metadata,
        )

    def get_episode(
        self,
        lang: str,
        episode_id: str,
    ) -> Episode | None:
        return _read_cache(
            cache_path=self.cache_path,
            file_name=f"episode.{episode_id}.{lang}.pickle",
        )

    def save_episode(self, lang: str, episode_id: str, episode: Episode):
        _save_cache(
            cache_path=self.cache_path,
            file_name=f"episode.{episode_id}.{lang}.pickle",
            data=episode,
        )

    def count_episode(self, lang: str) -> int:
        if not self.cache_path.exists():
            return 0

        with ZipFile(self.cache_path, "r", compression=ZIP_BZIP2) as cache_file:
            namelist = cache_file.namelist()

        return sum(
            [
                1
                for s in namelist
                if s.startswith("episode.") and s.endswith(f"{lang}.pickle")
            ]
        )
