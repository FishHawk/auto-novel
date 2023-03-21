package data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.litote.kmongo.eq
import java.time.LocalDateTime

@Serializable
data class User(
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

        @SerialName("normal")
        Normal,
    }
}

class UserRepository(
    private val mongoDataSource: MongoDataSource,
) {
    private val col
        get() = mongoDataSource.database.getCollection<User>("user")

    init {
        runBlocking {
            col.ensureUniqueIndex(User::email)
            col.ensureUniqueIndex(User::username)
        }
    }

    suspend fun add(user: User) {
        col.insertOne(user)
    }

    suspend fun getByEmail(email: String): User? {
        return col.findOne(User::email eq email)
    }

    suspend fun getByUsername(username: String): User? {
        return col.findOne(User::username eq username)
    }
}