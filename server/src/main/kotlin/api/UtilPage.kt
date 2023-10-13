package api

import infra.model.Page
import kotlinx.serialization.Serializable

@Serializable
data class PageDto<T>(
    val items: List<T>,
    val pageNumber: Long,
)

inline fun <T, R> Page<R>.asDto(
    pageSize: Int,
    transform: (R) -> T,
) = PageDto(
    items = items.map(transform),
    pageNumber = (total - 1) / pageSize + 1,
)

fun validatePageNumber(page: Int) {
    if (page < 0) {
        throwBadRequest("页码不应该小于0")
    }
}

fun validatePageSize(pageSize: Int, max: Int = 100) {
    if (pageSize < 1) {
        throwBadRequest("每页数据量不应该小于1")
    }
    if (pageSize > max) {
        throwBadRequest("每页数据量不应该大于${max}")
    }
}
