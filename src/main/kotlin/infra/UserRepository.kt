package infra

import com.mongodb.client.model.Aggregates.count
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.Facet
import com.mongodb.client.result.UpdateResult
import infra.model.*
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate
import org.litote.kmongo.id.toId
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

    suspend fun isUserFavoriteWebNovel(
        username: String,
        novelId: String,
    ): Boolean {
        return mongo.userCollection.countDocuments(
            and(
                User::username eq username,
                User::favoriteWebNovels contains ObjectId(novelId).toId(),
            ),
            CountOptions().limit(1),
        ) != 0L
    }

    suspend fun listFavoriteWebNovel(
        username: String,
        page: Int,
        pageSize: Int,
    ): Page<WebNovelMetadataOutline> {
        @Serializable
        data class NovelPage(val total: Int, val items: List<WebNovelMetadataOutline>)

        val doc = mongo
            .userCollection
            .aggregate<NovelPage>(
                project(
                    User::username from User::username,
                    User::favoriteWebNovels from Document(
                        "reverseArray".projection,
                        User::favoriteWebNovels.projection
                    )
                ),
                match(User.byUsername(username)),
                limit(1),
                unwind(User::favoriteWebNovels.path().projection),
                facet(
                    Facet("count", listOf(count())),
                    Facet(
                        "items", listOf(
                            skip(page * pageSize),
                            limit(pageSize),
                            lookup(
                                from = mongo.webNovelMetadataCollectionName,
                                localField = User::favoriteWebNovels.path(),
                                foreignField = WebNovelMetadata::id.path(),
                                newAs = "novel"
                            ),
                            unwind("novel".projection),
                            replaceRoot("novel".projection),
                            project(
                                WebNovelMetadata::providerId,
                                WebNovelMetadata::novelId,
                                WebNovelMetadata::titleJp,
                                WebNovelMetadata::titleZh,
                            ),
                        )
                    )
                ),
                project(
                    NovelPage::total from arrayElemAt("count.count".projection, 0),
                    NovelPage::items from "items".projection,
                )
            )
            .first()
        return if (doc == null) {
            emptyPage()
        } else {
            Page(total = doc.total.toLong(), items = doc.items)
        }
    }

    suspend fun addFavoriteWebNovel(
        username: String,
        novelId: String,
    ): UpdateResult {
        return mongo
            .userCollection
            .updateOne(
                User.byUsername(username),
                addToSet(User::favoriteWebNovels, ObjectId(novelId).toId()),
            )
    }

    suspend fun removeFavoriteWebNovel(
        username: String,
        novelId: String,
    ): UpdateResult {
        return mongo
            .userCollection
            .updateOne(
                User.byUsername(username),
                pull(User::favoriteWebNovels, ObjectId(novelId).toId()),
            )
    }

    suspend fun isUserFavoriteWenkuNovel(
        username: String,
        novelId: String,
    ): Boolean {
        return mongo.userCollection.countDocuments(
            and(
                User::username eq username,
                User::favoriteWenkuNovels contains ObjectId(novelId).toId(),
            ),
            CountOptions().limit(1),
        ) != 0L
    }

    suspend fun listFavoriteWenkuNovel(
        username: String,
        page: Int,
        pageSize: Int,
    ): Page<WenkuNovelMetadataOutline> {
        @Serializable
        data class NovelPage(val total: Int, val items: List<WenkuNovelMetadataOutline>)

        val doc = mongo
            .userCollection
            .aggregate<NovelPage>(
                project(
                    User::username from User::username,
                    User::favoriteWenkuNovels from Document(
                        "reverseArray".projection,
                        User::favoriteWenkuNovels.projection
                    )
                ),
                match(User.byUsername(username)),
                limit(1),
                unwind(User::favoriteWenkuNovels.path().projection),
                facet(
                    Facet("count", listOf(count())),
                    Facet(
                        "items", listOf(
                            skip(page * pageSize),
                            limit(pageSize),
                            lookup(
                                from = mongo.wenkuNovelMetadataCollectionName,
                                localField = User::favoriteWenkuNovels.path(),
                                foreignField = WenkuNovelMetadata::id.path(),
                                newAs = "novel"
                            ),
                            unwind("novel".projection),
                            replaceRoot("novel".projection),
                            project(
                                WenkuNovelMetadataOutline::id from Document(
                                    "toString".projection,
                                    WenkuNovelMetadata::id.projection
                                ),
                                WenkuNovelMetadataOutline::title from WenkuNovelMetadata::title,
                                WenkuNovelMetadataOutline::titleZh from WenkuNovelMetadata::titleZh,
                                WenkuNovelMetadataOutline::cover from WenkuNovelMetadata::cover,
                            )
                        )
                    )
                ),
                project(
                    "total" from arrayElemAt("count.count".projection, 0),
                    "items" from "items".projection,
                )
            )
            .first()
        return if (doc == null) {
            emptyPage()
        } else {
            Page(total = doc.total.toLong(), items = doc.items)
        }
    }

    suspend fun addFavoriteWenkuNovel(
        username: String,
        novelId: String,
    ): UpdateResult {
        return mongo
            .userCollection
            .updateOne(
                User.byUsername(username),
                addToSet(User::favoriteWenkuNovels, ObjectId(novelId).toId()),
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
                pull(User::favoriteWenkuNovels, ObjectId(novelId).toId()),
            )
    }
}