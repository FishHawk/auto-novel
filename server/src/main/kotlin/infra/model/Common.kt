package infra.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class Page<T>(
    val items: List<T>,
    val total: Long,
)

fun <T> emptyPage() = Page<T>(items = emptyList(), total = 0L)

@Serializable
enum class FavoriteListSort {
    @SerialName("create")
    CreateAt,

    @SerialName("update")
    UpdateAt,
}