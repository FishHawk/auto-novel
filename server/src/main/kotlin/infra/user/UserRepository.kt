package infra.user

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.set
import infra.MongoClient
import infra.MongoCollectionNames
import infra.RedisClient
import infra.common.Page
import infra.field
import io.github.crackthecodeabhi.kreds.args.SetOption
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import org.bson.types.ObjectId
import util.PBKDF2
import util.serialName
import kotlin.time.Duration.Companion.minutes

class UserRepository(
    mongo: MongoClient,
    private val redis: RedisClient,
) {
    private val userCollection =
        mongo.database.getCollection<User>(
            MongoCollectionNames.USER,
        )

    suspend fun listUser(
        page: Int,
        pageSize: Int,
        role: UserRole,
    ): Page<User> {
        val users = userCollection
            .find(eq(User::role.field(), role.serialName()))
            .skip(page * pageSize)
            .limit(pageSize)
            .toList()

        val total = userCollection
            .countDocuments()

        return Page(
            items = users,
            total = total,
            pageSize = pageSize,
        )
    }

    suspend fun add(
        email: String,
        username: String,
        password: String,
    ): ObjectId {
        val salt = PBKDF2.randomSalt()
        val hashedPassword = PBKDF2.hash(password, salt)
        return userCollection
            .insertOne(
                User(
                    id = ObjectId(),
                    email = email,
                    username = username,
                    salt = salt,
                    password = hashedPassword,
                    role = UserRole.Normal,
                    favoredWeb = listOf(UserFavored(id = "default", title = "默认收藏夹")),
                    favoredWenku = listOf(UserFavored(id = "default", title = "默认收藏夹")),
                    createdAt = Clock.System.now(),
                )
            ).insertedId!!.asObjectId().value
    }

    suspend fun updatePassword(userId: ObjectId, password: String) {
        val salt = PBKDF2.randomSalt()
        val hashedPassword = PBKDF2.hash(password, salt)
        userCollection
            .updateOne(
                eq(User::id.field(), userId),
                combine(
                    set(User::salt.field(), salt),
                    set(User::password.field(), hashedPassword),
                )
            )
    }

    suspend fun updateRole(userId: ObjectId, role: UserRole) {
        userCollection
            .updateOne(
                eq(User::id.field(), userId),
                set(User::role.field(), role.serialName()),
            )
    }

    suspend fun getById(id: String): User? {
        return userCollection
            .find(
                eq(User::id.field(), ObjectId(id)),
            )
            .firstOrNull()
    }

    suspend fun getByEmail(email: String): User? {
        return userCollection
            .find(
                eq(User::email.field(), email),
            )
            .firstOrNull()
    }

    suspend fun getByUsername(username: String): User? {
        return userCollection
            .find(
                eq(User::username.field(), username),
            )
            .firstOrNull()
    }

    suspend fun getByUsernameOrEmail(emailOrUsername: String): User? {
        return userCollection
            .find(
                or(
                    eq(User::email.field(), emailOrUsername),
                    eq(User::username.field(), emailOrUsername),
                )
            )
            .firstOrNull()
    }

    private fun emailCodeRedisKey(email: String) = "ec:${email}"

    suspend fun validateEmailCode(email: String, emailCode: String): Boolean {
        return redis.get(emailCodeRedisKey(email)) == emailCode
    }

    suspend fun addEmailCode(email: String, emailCode: String) {
        redis.set(
            key = emailCodeRedisKey(email),
            value = emailCode,
            setOption = SetOption.Builder()
                .exSeconds(15.minutes.inWholeSeconds.toULong())
                .build(),
        )
    }

    private fun resetPasswordTokenRedisKey(id: ObjectId) = "rpt:${id.toHexString()}"

    suspend fun validateResetPasswordToken(id: ObjectId, token: String): Boolean {
        return redis.get(resetPasswordTokenRedisKey(id)) == token
    }

    suspend fun addResetPasswordToken(id: ObjectId, token: String) {
        redis.set(
            key = resetPasswordTokenRedisKey(id),
            value = token,
            setOption = SetOption.Builder()
                .exSeconds(15.minutes.inWholeSeconds.toULong())
                .build(),
        )
    }

    suspend fun updateFavoredWeb(
        userId: ObjectId,
        favored: List<UserFavored>,
    ) {
        userCollection
            .updateOne(
                eq(User::id.field(), userId),
                set(User::favoredWeb.field(), favored)
            )
    }

    suspend fun updateFavoredWenku(
        userId: ObjectId,
        favored: List<UserFavored>,
    ) {
        userCollection
            .updateOne(
                eq(User::id.field(), userId),
                set(User::favoredWenku.field(), favored),
            )
    }
}