package infra.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Page<T>(
    val items: List<T>,
    val pageNumber: Long,
) {
    constructor(items: List<T>, total: Long, pageSize: Int) : this(
        items = items,
        pageNumber = (total - 1) / pageSize + 1,
    )

    inline fun <R> map(transform: (T) -> R) = Page(
        items = items.map(transform),
        pageNumber = pageNumber,
    )
}


fun <T> emptyPage() = Page<T>(items = emptyList(), pageNumber = 0L)

@Serializable
enum class FavoredNovelListSort {
    @SerialName("create")
    CreateAt,

    @SerialName("update")
    UpdateAt,
}