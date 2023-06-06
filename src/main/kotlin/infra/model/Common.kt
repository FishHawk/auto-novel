package infra.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.Id
import org.litote.kmongo.eq
import java.time.LocalDateTime

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

@Serializable
data class Comment(
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
    @Contextual @SerialName("_id") val id: ObjectId,
    val email: String,
    val username: String,
    val salt: String,
    val password: String,
    val role: Role,
    @Contextual val createdAt: LocalDateTime,
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

    companion object {
        fun byUsername(username: String) = User::username eq username
    }
}
