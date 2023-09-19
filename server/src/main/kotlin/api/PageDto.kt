package api

import infra.model.Page
import kotlinx.serialization.Serializable

@Serializable
class PageDto<T>(
    val items: List<T>,
    val pageNumber: Long,
) {
    companion object {
        inline fun <T, R> fromPage(
            page: Page<R>,
            pageSize: Int,
            transform: (R) -> T,
        ) = PageDto(
            items = page.items.map(transform),
            pageNumber = (page.total - 1) / pageSize + 1,
        )
    }
}
