package infra.common

import com.jillesvangurp.ktsearch.document
import com.jillesvangurp.ktsearch.getDocument
import com.mongodb.client.model.Aggregates.count
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.Facet
import com.mongodb.client.model.UpdateOptions
import infra.*
import infra.model.*
import infra.web.toOutline
import io.github.crackthecodeabhi.kreds.args.SetOption
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate
import org.litote.kmongo.id.toId
import util.PBKDF2
import kotlin.time.Duration.Companion.minutes

class UserRepository(
    private val mongo: DataSourceMongo,
    private val es: DataSourceElasticSearch,
    private val redis: DataSourceRedis,
) {
    suspend fun add(
        email: String,
        username: String,
        password: String,
    ): ObjectId {
        val salt = PBKDF2.randomSalt()
        val hashedPassword = PBKDF2.hash(password, salt)
        return mongo
            .userCollection
            .insertOne(
                User(
                    id = ObjectId(),
                    email = email,
                    username = username,
                    salt = salt,
                    password = hashedPassword,
                    role = User.Role.Normal,
                    createdAt = Clock.System.now(),
                )
            ).insertedId!!.asObjectId().value
    }

    suspend fun updatePassword(userId: ObjectId, password: String) {
        val salt = PBKDF2.randomSalt()
        val hashedPassword = PBKDF2.hash(password, salt)
        mongo
            .userCollection
            .updateOne(
                User::id eq userId,
                combine(
                    setValue(User::salt, salt),
                    setValue(User::password, hashedPassword),
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

    suspend fun getByUsernameOrEmail(emailOrUsername: String): User? {
        return mongo
            .userCollection
            .findOne(
                or(
                    User::email eq emailOrUsername,
                    User::username eq emailOrUsername,
                )
            )
    }

    suspend fun getReaderHistory(
        userId: String,
        novelId: String,
    ): WebNovelReadHistoryModel? {
        return mongo
            .webNovelReadHistoryCollection
            .findOne(
                and(
                    WebNovelReadHistoryModel::userId eq ObjectId(userId).toId(),
                    WebNovelReadHistoryModel::novelId eq ObjectId(novelId).toId(),
                ),
            )
    }

    suspend fun listReaderHistoryWebNovel(
        userId: String,
        page: Int,
        pageSize: Int,
    ): Page<WebNovelMetadataOutline> {
        @Serializable
        data class NovelPage(val total: Int = 0, val items: List<WebNovelMetadata>)

        val doc = mongo
            .webNovelReadHistoryCollection
            .aggregate<NovelPage>(
                match(WebNovelReadHistoryModel::userId eq ObjectId(userId).toId()),
                sort(Document(WebNovelReadHistoryModel::createAt.path(), -1)),
                facet(
                    Facet("count", count()),
                    Facet(
                        "items",
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            from = mongo.webNovelMetadataCollectionName,
                            localField = WebNovelReadHistoryModel::novelId.path(),
                            foreignField = WebNovelMetadata::id.path(),
                            newAs = "novel"
                        ),
                        unwind("novel".projection),
                        replaceRoot("novel".projection),
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
            Page(
                total = doc.total.toLong(),
                items = doc.items.map { it.toOutline() },
            )
        }
    }

    suspend fun updateReadHistoryWebNovel(
        userId: String,
        novelId: String,
        chapterId: String,
    ) {
        mongo
            .webNovelReadHistoryCollection
            .updateOne(
                and(
                    WebNovelFavoriteModel::userId eq ObjectId(userId).toId(),
                    WebNovelFavoriteModel::novelId eq ObjectId(novelId).toId(),
                ),
                WebNovelReadHistoryModel(
                    userId = ObjectId(userId).toId(),
                    novelId = ObjectId(novelId).toId(),
                    chapterId = chapterId,
                    createAt = Clock.System.now(),
                ),
                UpdateOptions().upsert(true),
            )
    }

    suspend fun isUserFavoriteWebNovel(
        userId: String,
        novelId: String,
    ): Boolean {
        return mongo.webNovelFavoriteCollection.countDocuments(
            and(
                WebNovelFavoriteModel::userId eq ObjectId(userId).toId(),
                WebNovelFavoriteModel::novelId eq ObjectId(novelId).toId(),
            ),
            CountOptions().limit(1),
        ) != 0L
    }

    suspend fun listFavoriteWebNovel(
        userId: String,
        page: Int,
        pageSize: Int,
        sort: FavoriteListSort,
    ): Page<WebNovelMetadataOutline> {
        @Serializable
        data class NovelPage(val total: Int = 0, val items: List<WebNovelMetadata>)

        val sortProperty = when (sort) {
            FavoriteListSort.CreateAt -> WebNovelFavoriteModel::createAt
            FavoriteListSort.UpdateAt -> WebNovelFavoriteModel::updateAt
        }

        val doc = mongo
            .webNovelFavoriteCollection
            .aggregate<NovelPage>(
                match(WebNovelFavoriteModel::userId eq ObjectId(userId).toId()),
                sort(Document(sortProperty.path(), -1)),
                facet(
                    Facet("count", count()),
                    Facet(
                        "items",
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            from = mongo.webNovelMetadataCollectionName,
                            localField = WebNovelFavoriteModel::novelId.path(),
                            foreignField = WebNovelMetadata::id.path(),
                            newAs = "novel"
                        ),
                        unwind("novel".projection),
                        replaceRoot("novel".projection),
                    )
                ),
                project(
                    NovelPage::total from arrayElemAt("count.count".projection, 0),
                    NovelPage::items from "items".projection,
                ),
            )
            .first()
        return if (doc == null) {
            emptyPage()
        } else {
            Page(
                total = doc.total.toLong(),
                items = doc.items.map { it.toOutline() },
            )
        }
    }

    suspend fun countFavoriteWebNovelByUserId(
        userId: String,
    ): Long {
        return mongo
            .webNovelFavoriteCollection
            .countDocuments(
                WebNovelFavoriteModel::userId eq ObjectId(userId).toId(),
            )
    }

    suspend fun addFavoriteWebNovel(
        userId: String,
        novelId: String,
    ) {
        val novel = mongo
            .webNovelMetadataCollection
            .findOneById(ObjectId(novelId))!!
        mongo
            .webNovelFavoriteCollection
            .insertOne(
                WebNovelFavoriteModel(
                    userId = ObjectId(userId).toId(),
                    novelId = ObjectId(novelId).toId(),
                    createAt = Clock.System.now(),
                    updateAt = novel.updateAt,
                )
            )
    }

    suspend fun removeFavoriteWebNovel(
        userId: String,
        novelId: String,
    ) {
        mongo
            .webNovelFavoriteCollection
            .deleteOne(
                WebNovelFavoriteModel::userId eq ObjectId(userId).toId(),
                WebNovelFavoriteModel::novelId eq ObjectId(novelId).toId(),
            )
    }

    suspend fun isUserFavoriteWenkuNovel(
        userId: String,
        novelId: String,
    ): Boolean {
        return mongo.wenkuNovelFavoriteCollection.countDocuments(
            and(
                WenkuNovelFavoriteModel::userId eq ObjectId(userId).toId(),
                WenkuNovelFavoriteModel::novelId eq ObjectId(novelId).toId(),
            ),
            CountOptions().limit(1),
        ) != 0L
    }

    suspend fun listFavoriteWenkuNovel(
        userId: String,
        page: Int,
        pageSize: Int,
        sort: FavoriteListSort,
    ): Page<WenkuNovelMetadataOutline> {
        @Serializable
        data class NovelPage(val total: Int = 0, val items: List<WenkuNovelMetadata>)

        val sortProperty = when (sort) {
            FavoriteListSort.CreateAt -> WenkuNovelFavoriteModel::createAt
            FavoriteListSort.UpdateAt -> WenkuNovelFavoriteModel::updateAt
        }

        val doc = mongo
            .wenkuNovelFavoriteCollection
            .aggregate<NovelPage>(
                match(WenkuNovelFavoriteModel::userId eq ObjectId(userId).toId()),
                sort(Document(sortProperty.path(), -1)),
                facet(
                    Facet("count", count()),
                    Facet(
                        "items",
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            from = mongo.wenkuNovelMetadataCollectionName,
                            localField = WenkuNovelFavoriteModel::novelId.path(),
                            foreignField = WenkuNovelMetadata::id.path(),
                            newAs = "novel"
                        ),
                        unwind("novel".projection),
                        replaceRoot("novel".projection),
                    )
                ),
                project(
                    NovelPage::total from arrayElemAt("count.count".projection, 0),
                    NovelPage::items from "items".projection,
                ),
            )
            .first()
        return if (doc == null) {
            emptyPage()
        } else {
            Page(
                total = doc.total.toLong(),
                items = doc.items.map {
                    WenkuNovelMetadataOutline(
                        id = it.id.toHexString(),
                        title = it.title,
                        titleZh = it.titleZh,
                        cover = it.cover,
                    )
                },
            )
        }
    }

    suspend fun countFavoriteWenkuNovelByUserId(
        userId: String,
    ): Long {
        return mongo
            .wenkuNovelFavoriteCollection
            .countDocuments(
                WenkuNovelFavoriteModel::userId eq ObjectId(userId).toId(),
            )
    }

    suspend fun addFavoriteWenkuNovel(
        userId: String,
        novelId: String,
    ) {
        val esnovel = es.client.getDocument(
            target = DataSourceElasticSearch.wenkuNovelIndexName,
            id = novelId,
        ).document<WenkuNovelMetadataEsModel>()
        mongo
            .wenkuNovelFavoriteCollection
            .insertOne(
                WenkuNovelFavoriteModel(
                    userId = ObjectId(userId).toId(),
                    novelId = ObjectId(novelId).toId(),
                    createAt = Clock.System.now(),
                    updateAt = Instant.fromEpochSeconds(esnovel.updateAt),
                )
            )
    }

    suspend fun removeFavoriteWenkuNovel(
        userId: String,
        novelId: String,
    ) {
        mongo
            .wenkuNovelFavoriteCollection
            .deleteOne(
                WenkuNovelFavoriteModel::userId eq ObjectId(userId).toId(),
                WenkuNovelFavoriteModel::novelId eq ObjectId(novelId).toId(),
            )
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
}