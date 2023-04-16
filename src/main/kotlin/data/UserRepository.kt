package data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.litote.kmongo.addToSet
import org.litote.kmongo.combine
import org.litote.kmongo.eq
import org.litote.kmongo.pull
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
        val bookId: String,
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
        return col.findOne(User::username eq username)
    }

    suspend fun addFavorite(
        username: String,
        providerId: String,
        bookId: String,
    ) {
        col.updateOne(
            User::username eq username,
            combine(
                addToSet(
                    User::favoriteBooks, User.FavoriteBook(
                        providerId = providerId,
                        bookId = bookId,
                    )
                ),
            )
        )
    }

    suspend fun removeFavorite(
        username: String,
        providerId: String,
        bookId: String,
    ) {
        col.updateOne(
            User::username eq username,
            combine(
                pull(
                    User::favoriteBooks, User.FavoriteBook(
                        providerId = providerId,
                        bookId = bookId,
                    )
                ),
            )
        )
    }
}