package infra.user

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class UserOutline(
    val username: String,
    val role: UserRole,
)

@Serializable
data class UserFavored(
    val id: String,
    val title: String,
)

@Serializable
data class User(
    @Contextual @SerialName("_id") val id: ObjectId,
    val email: String,
    val username: String,
    val salt: String,
    val password: String,
    val role: UserRole,
    val favoredWeb: List<UserFavored>,
    val favoredWenku: List<UserFavored>,
    @Contextual val createdAt: Instant,
)

@Serializable
enum class UserRole {
    @SerialName("admin")
    Admin,

    @SerialName("maintainer")
    Maintainer,

    @SerialName("trusted")
    Trusted,

    @SerialName("normal")
    Normal,

    @SerialName("banned")
    Banned;

    private fun authLevel() = when (this) {
        Admin -> 4
        Maintainer -> 3
        Trusted -> 2
        Normal -> 1
        Banned -> 0
    }

    infix fun atLeast(other: UserRole): Boolean =
        authLevel() >= other.authLevel()
}