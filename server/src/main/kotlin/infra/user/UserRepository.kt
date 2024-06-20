package infra.user

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.Projections.*
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.set
import infra.MongoClient
import infra.MongoCollectionNames
import infra.common.Page
import infra.field
import infra.toString
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import org.bson.types.ObjectId
import util.PBKDF2
import util.serialName

class UserRepository(
    mongo: MongoClient,
) {
    private val userCollection =
        mongo.database.getCollection<UserDbModel>(
            MongoCollectionNames.USER,
        )

    private val userProjection = fields(
        computed(
            User::id.field(),
            toString(UserDbModel::id.field()),
        ),
        include(
            User::email.field(),
            User::username.field(),
            User::role.field(),
            User::createdAt.field(),
        )
    )

    suspend fun listUser(
        page: Int,
        pageSize: Int,
        role: UserRole,
    ): Page<User> {
        val users = userCollection
            .withDocumentClass<User>()
            .find(eq(UserDbModel::role.field(), role))
            .skip(page * pageSize)
            .limit(pageSize)
            .projection(userProjection)
            .toList()

        val total = userCollection
            .estimatedDocumentCount()

        return Page(
            items = users,
            total = total,
            pageSize = pageSize,
        )
    }

    suspend fun getUser(
        id: String,
    ): User? {
        return userCollection
            .withDocumentClass<User>()
            .find(eq(UserDbModel::id.field(), ObjectId(id)))
            .projection(userProjection)
            .firstOrNull()
    }

    suspend fun getUserByEmail(email: String): User? {
        return userCollection
            .withDocumentClass<User>()
            .find(eq(UserDbModel::email.field(), email))
            .projection(userProjection)
            .firstOrNull()
    }

    suspend fun getUserByUsername(username: String): User? {
        return userCollection
            .withDocumentClass<User>()
            .find(eq(UserDbModel::username.field(), username))
            .projection(userProjection)
            .firstOrNull()
    }

    suspend fun getUserByUsernameOrEmail(emailOrUsername: String): User? {
        return userCollection
            .withDocumentClass<User>()
            .find(
                or(
                    eq(UserDbModel::email.field(), emailOrUsername),
                    eq(UserDbModel::username.field(), emailOrUsername),
                )
            )
            .projection(userProjection)
            .firstOrNull()
    }

    suspend fun getUserWithPasswordVerify(
        emailOrUsername: String,
        password: String,
    ): User? {
        val model = userCollection
            .find(
                or(
                    eq(UserDbModel::email.field(), emailOrUsername),
                    eq(UserDbModel::username.field(), emailOrUsername),
                )
            )
            .firstOrNull()
        return if (
            model == null || model.password != PBKDF2.hash(password, model.salt)
        ) {
            null
        } else {
            User(
                id = model.id.toHexString(),
                email = model.email,
                username = model.username,
                role = model.role,
                createdAt = model.createdAt
            )
        }
    }

    suspend fun addUser(
        email: String,
        username: String,
        password: String,
    ): User {
        val salt = PBKDF2.randomSalt()
        val hashedPassword = PBKDF2.hash(password, salt)
        val model = UserDbModel(
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
        val userId = userCollection
            .insertOne(model)
            .insertedId!!.asObjectId().value
        return User(
            id = userId.toHexString(),
            email = model.email,
            username = model.username,
            role = model.role,
            createdAt = model.createdAt
        )
    }

    suspend fun updatePassword(
        userId: String,
        password: String,
    ) {
        val salt = PBKDF2.randomSalt()
        val hashedPassword = PBKDF2.hash(password, salt)
        userCollection
            .updateOne(
                eq(UserDbModel::id.field(), ObjectId(userId)),
                combine(
                    set(UserDbModel::salt.field(), salt),
                    set(UserDbModel::password.field(), hashedPassword),
                )
            )
    }

    suspend fun updateRole(
        userId: String,
        role: UserRole,
    ) {
        userCollection
            .updateOne(
                eq(UserDbModel::id.field(), ObjectId(userId)),
                set(UserDbModel::role.field(), role.serialName()),
            )
    }
}