package infra

import com.mongodb.client.result.UpdateResult
import infra.model.User
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.addToSet
import org.litote.kmongo.eq
import org.litote.kmongo.pull
import util.PBKDF2
import java.time.LocalDateTime


class UserRepository(
    private val mongo: MongoDataSource,
) {
    suspend fun add(
        email: String,
        username: String,
        password: String,
    ) {
        val salt = PBKDF2.randomSalt()
        val hashedPassword = PBKDF2.hash(password, salt)
        mongo
            .userCollection
            .insertOne(
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
        return mongo
            .userCollection
            .findOne(User::email eq email)
    }

    suspend fun getByUsername(username: String): User? {
        return mongo
            .userCollection
            .findOne(User.byUsername(username))
    }

    suspend fun listFavoriteWebNovel(username: String): List<User.FavoriteBook>? {
        @Serializable
        data class UserProjection(val favoriteBooks: List<User.FavoriteBook> = emptyList())
        return mongo
            .userCollection
            .withDocumentClass<UserProjection>()
            .find(User.byUsername(username))
            .projection(User::favoriteBooks)
            .first()
            ?.favoriteBooks
            ?.reversed()
    }

    suspend fun addFavoriteWebNovel(
        username: String,
        providerId: String,
        novelId: String,
    ): UpdateResult {
        return mongo
            .userCollection
            .updateOne(
                User.byUsername(username),
                addToSet(User::favoriteBooks, User.FavoriteBook(providerId, novelId)),
            )
    }

    suspend fun removeFavoriteWebNovel(
        username: String,
        providerId: String,
        novelId: String,
    ): UpdateResult {
        return mongo
            .userCollection
            .updateOne(
                User.byUsername(username),
                pull(User::favoriteBooks, User.FavoriteBook(providerId, novelId)),
            )
    }

    suspend fun listFavoriteWenkuNovel(username: String): List<String>? {
        @Serializable
        data class UserProjection(val favoriteWenkuBooks: List<String> = emptyList())
        return mongo
            .userCollection
            .withDocumentClass<UserProjection>()
            .find(User.byUsername(username))
            .projection(User::favoriteWenkuBooks)
            .first()
            ?.favoriteWenkuBooks
            ?.reversed()
    }

    suspend fun addFavoriteWenkuNovel(
        username: String,
        novelId: String,
    ): UpdateResult {
        return mongo
            .userCollection
            .updateOne(
                User.byUsername(username),
                addToSet(User::favoriteWenkuBooks, novelId),
            )
    }

    suspend fun removeFavoriteWenkuNovel(
        username: String,
        novelId: String,
    ): UpdateResult {
        return mongo
            .userCollection
            .updateOne(
                User.byUsername(username),
                pull(User::favoriteWenkuBooks, novelId),
            )
    }
}