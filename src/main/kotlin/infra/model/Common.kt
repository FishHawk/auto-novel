package infra.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.litote.kmongo.eq
import java.time.LocalDateTime

data class Page<T>(
    val items: List<T>,
    val total: Long,
)

@Serializable
data class CommentView(
    val id: String,
    val createAt: Int,
    val parentId: String?,
    val username: String,
    val receiver: String?,
    val upvote: Int,
    val downvote: Int,
    val viewerVote: Boolean?,
    val content: String,
)

@Serializable
data class User(
    val email: String,
    val username: String,
    val salt: String,
    val password: String,
    val role: Role,
    @Contextual val createdAt: LocalDateTime,
    val favoriteBooks: List<FavoriteBook> = emptyList(),
    val favoriteWenkuBooks: List<String> = emptyList(),
) {
    @Serializable
    enum class Role {
        @SerialName("admin")
        Admin,

        @SerialName("maintainer")
        Maintainer,

        @SerialName("normal")
        Normal,
    }

    @Serializable
    data class FavoriteBook(
        val providerId: String,
        @SerialName("bookId")
        val novelId: String,
    )

    companion object {
        fun byUsername(username: String) = User::username eq username
    }
}
