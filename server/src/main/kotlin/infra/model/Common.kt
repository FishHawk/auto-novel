package infra.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.eq

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
data class UserOutline(
    val username: String,
    val role: User.Role,
)

@Serializable
data class User(
    @Contextual @SerialName("_id") val id: ObjectId,
    val email: String,
    val username: String,
    val salt: String,
    val password: String,
    val role: Role,
    @Contextual val createdAt: Instant,
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
