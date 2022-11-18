from abc import abstractmethod
from typing import Iterator, List

from app.translator.base import Translator


class BaiduQueryProcessor:
    def __init__(self, query_list: List[str]):
        self.splited_query_list: List[str] = []
        self.splited_index: List[int] = []

        for query in query_list:
            splited = query.split("\n")
            self.splited_query_list += splited
            self.splited_index.append(len(splited))

        self.filtered_splited_query_list = [
            q for q in self.splited_query_list if q.strip()
        ]

    def get(self) -> List[str]:
        return self.filtered_splited_query_list

    def _concat(self, result_list: List[str]):
        assert len(result_list) == len(self.splited_query_list)
        recovered = []
        for result in result_list:
            recovered.append(result)
            if len(recovered) == self.splited_index[0]:
                self.splited_index.pop(0)
                yield "\n".join(recovered)
                recovered = []

    def recover(self, result_list: List[str]) -> List[str]:
        assert len(result_list) == len(self.filtered_splited_query_list)
        inserted_result_list = []
        for query in self.splited_query_list:
            if query.strip():
                inserted_result_list.append(result_list.pop(0))
            else:
                inserted_result_list.append(query)
        return list(self._concat(inserted_result_list))


class BaiduBaseTranslate(Translator):
    _limit_per_request = 2000

    def _chunk_query(self, query: List[str]) -> Iterator[str | List[str]]:
        chunked = []
        chunked_size = 0
        for line in query:
            line_size = len(line)
            if line_size + chunked_size <= self._limit_per_request:
                chunked.append(line)
                chunked_size += line_size
            else:
                if chunked:
                    yield chunked
                    chunked = []
                    chunked_size = 0
                if line_size <= self._limit_per_request:
                    chunked.append(line)
                    chunked_size += line_size
                else:
                    yield line
        if chunked:
            yield chunked

    def _chunk_string(self, query: str) -> Iterator[str]:
        return [
            query[i : i + self._limit_per_request]
            for i in range(0, len(query), self._limit_per_request)
        ]

    @abstractmethod
    def _inner_translate(
        self,
        query: str,
    ) -> List[str]:
        pass

    def _translate(
        self,
        query_list: List[str],
    ) -> List[str]:
        processor = BaiduQueryProcessor(query_list=query_list)
        processed_query_list = processor.get()

        result_list = []
        for chunked_query in self._chunk_query(processed_query_list):
            if isinstance(chunked_query, str):
                buffer_result_list = []
                for chunked_string in self._chunk_string(chunked_query):
                    chunked_translated = self._inner_translate(
                        query=chunked_string,
                    )
                    buffer_result_list += chunked_translated
                result_list.append("".join(buffer_result_list))
            else:
                chunked_translated = self._inner_translate(
                    query="\n".join(chunked_query),
                )
                result_list += chunked_translated

        recovered_result_list = processor.recover(result_list=result_list)

        if len(recovered_result_list) != len(query_list):
            raise Exception("Baidu translator error")

        return recovered_result_list
