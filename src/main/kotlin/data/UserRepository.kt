package data

import com.mongodb.client.result.UpdateResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.*
import util.PBKDF2
import java.time.LocalDateTime

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

    fun validatePassword(password: String): Boolean {
        return this.password == PBKDF2.hash(password, salt)
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

    companion object {
        private fun byUsername(username: String): Bson = User::username eq username
    }

    suspend fun add(
        email: String,
        username: String,
        password: String,
    ) {
        val salt = PBKDF2.randomSalt()
        val hashedPassword = PBKDF2.hash(password, salt)
        col.insertOne(
            User(
                email = email,
                username = username,
                salt = salt,
                password = hashedPassword,
                role = User.Role.Normal,
                createdAt = LocalDateTime.now(),
                favoriteBooks = emptyList(),
            )
        )
    }

    suspend fun getByEmail(email: String): User? {
        return col.findOne(User::email eq email)
    }

    suspend fun getByUsername(username: String): User? {
        return col.findOne(byUsername(username))
    }

    suspend fun listFavoriteWebBook(username: String): List<User.FavoriteBook>? {
        @Serializable
        data class UserProjection(val favoriteBooks: List<User.FavoriteBook> = emptyList())
        return col
            .withDocumentClass<UserProjection>()
            .find(byUsername(username))
            .projection(User::favoriteBooks)
            .first()
            ?.favoriteBooks
            ?.reversed()
    }

    suspend fun addFavoriteWebBook(
        username: String,
        providerId: String,
        novelId: String,
    ): UpdateResult {
        return col.updateOne(
            byUsername(username),
            addToSet(User::favoriteBooks, User.FavoriteBook(providerId, novelId)),
        )
    }

    suspend fun removeFavoriteWebBook(
        username: String,
        providerId: String,
        novelId: String,
    ): UpdateResult {
        return col.updateOne(
            byUsername(username),
            pull(User::favoriteBooks, User.FavoriteBook(providerId, novelId)),
        )
    }

    suspend fun listFavoriteWenkuBook(username: String): List<String>? {
        @Serializable
        data class UserProjection(val favoriteWenkuBooks: List<String> = emptyList())
        return col
            .withDocumentClass<UserProjection>()
            .find(byUsername(username))
            .projection(User::favoriteWenkuBooks)
            .first()
            ?.favoriteWenkuBooks
            ?.reversed()
    }

    suspend fun addFavoriteWenkuBook(
        username: String,
        novelId: String,
    ): UpdateResult {
        return col.updateOne(
            byUsername(username),
            addToSet(User::favoriteWenkuBooks, novelId),
        )
    }

    suspend fun removeFavoriteWenkuBook(
        username: String,
        novelId: String,
    ): UpdateResult {
        return col.updateOne(
            byUsername(username),
            pull(User::favoriteWenkuBooks, novelId),
        )
    }
}