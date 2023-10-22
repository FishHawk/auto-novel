package infra.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.eq

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

        @SerialName("banned")
        Banned,
    }

    companion object {
        fun byUsername(username: String) = User::username eq username
    }
}
