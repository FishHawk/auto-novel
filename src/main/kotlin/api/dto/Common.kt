package api.dto

import infra.model.Page
import kotlinx.serialization.Serializable

@Serializable
data class PageDto<T>(
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

@Serializable
data class SubCommentDto(
    val id: String,
    val createAt: Int,
    val username: String,
    val receiver: String?,
    val upvote: Int,
    val downvote: Int,
    val viewerVote: Boolean?,
    val content: String,
)

@Serializable
data class CommentDto(
    val id: String,
    val createAt: Int,
    val username: String,
    val upvote: Int,
    val downvote: Int,
    val viewerVote: Boolean?,
    val content: String,
    val pageNumber: Int,
    val items: List<SubCommentDto>,
)
