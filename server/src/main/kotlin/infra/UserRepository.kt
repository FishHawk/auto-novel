package infra

import com.jillesvangurp.ktsearch.document
import com.jillesvangurp.ktsearch.getDocument
import com.mongodb.client.model.Aggregates.count
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.Facet
import com.mongodb.client.model.UpdateOptions
import infra.model.*
import infra.web.toOutline
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate
import org.litote.kmongo.id.toId
import util.PBKDF2

class UserRepository(
    private val mongo: MongoDataSource,
    private val es: ElasticSearchDataSource,
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
                    id = ObjectId(),
                    email = email,
                    username = username,
                    salt = salt,
                    password = hashedPassword,
                    role = User.Role.Normal,
                    createdAt = Clock.System.now(),
                )
            )
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

    suspend fun getUserIdByUsername(username: String): ObjectId {
        return mongo
            .userCollection
            .withDocumentClass<Document>()
            .find(User.byUsername(username))
            .projection(User::id)
            .first()!!
            .findValue(User::id)!!
    }

    suspend fun getReaderHistory(
        username: String,
        novelId: String,
    ): WebNovelReadHistoryModel? {
        return mongo
            .webNovelReadHistoryCollection
            .findOne(
                and(
                    WebNovelReadHistoryModel::userId eq getUserIdByUsername(username).toId(),
                    WebNovelReadHistoryModel::novelId eq ObjectId(novelId).toId(),
                ),
            )
    }

    suspend fun listReaderHistoryWebNovel(
        username: String,
        page: Int,
        pageSize: Int,
    ): Page<WebNovelMetadataOutline> {
        @Serializable
        data class NovelPage(val total: Int = 0, val items: List<WebNovelMetadata>)

        val doc = mongo
            .webNovelReadHistoryCollection
            .aggregate<NovelPage>(
                match(WebNovelReadHistoryModel::userId eq getUserIdByUsername(username).toId()),
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
        username: String,
        novelId: String,
        chapterId: String,
    ) {
        mongo
            .webNovelReadHistoryCollection
            .updateOne(
                and(
                    WebNovelFavoriteModel::userId eq getUserIdByUsername(username).toId(),
                    WebNovelFavoriteModel::novelId eq ObjectId(novelId).toId(),
                ),
                WebNovelReadHistoryModel(
                    userId = getUserIdByUsername(username).toId(),
                    novelId = ObjectId(novelId).toId(),
                    chapterId = chapterId,
                    createAt = Clock.System.now(),
                ),
                UpdateOptions().upsert(true),
            )
    }

    suspend fun isUserFavoriteWebNovel(
        username: String,
        novelId: String,
    ): Boolean {
        return mongo.webNovelFavoriteCollection.countDocuments(
            and(
                WebNovelFavoriteModel::userId eq getUserIdByUsername(username).toId(),
                WebNovelFavoriteModel::novelId eq ObjectId(novelId).toId(),
            ),
            CountOptions().limit(1),
        ) != 0L
    }

    suspend fun listFavoriteWebNovel(
        username: String,
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
                match(WebNovelFavoriteModel::userId eq getUserIdByUsername(username).toId()),
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

    suspend fun addFavoriteWebNovel(
        username: String,
        novelId: String,
    ) {
        val novel = mongo
            .webNovelMetadataCollection
            .findOneById(ObjectId(novelId))!!
        val esnovel = es.client.getDocument(
            target = ElasticSearchDataSource.webNovelIndexName,
            id = "${novel.providerId}.${novel.novelId}",
        ).document<WebNovelMetadataEsModel>()
        mongo
            .webNovelFavoriteCollection
            .insertOne(
                WebNovelFavoriteModel(
                    userId = getUserIdByUsername(username).toId(),
                    novelId = ObjectId(novelId).toId(),
                    createAt = Clock.System.now(),
                    updateAt = Instant.fromEpochSeconds(esnovel.updateAt),
                )
            )
    }

    suspend fun removeFavoriteWebNovel(
        username: String,
        novelId: String,
    ) {
        mongo
            .webNovelFavoriteCollection
            .deleteOne(
                WebNovelFavoriteModel::userId eq getUserIdByUsername(username).toId(),
                WebNovelFavoriteModel::novelId eq ObjectId(novelId).toId(),
            )
    }

    suspend fun isUserFavoriteWenkuNovel(
        username: String,
        novelId: String,
    ): Boolean {
        return mongo.wenkuNovelFavoriteCollection.countDocuments(
            and(
                WenkuNovelFavoriteModel::userId eq getUserIdByUsername(username).toId(),
                WenkuNovelFavoriteModel::novelId eq ObjectId(novelId).toId(),
            ),
            CountOptions().limit(1),
        ) != 0L
    }

    suspend fun listFavoriteWenkuNovel(
        username: String,
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
                match(WenkuNovelFavoriteModel::userId eq getUserIdByUsername(username).toId()),
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
                        cover = it.coverSmall,
                    )
                },
            )
        }
    }

    suspend fun addFavoriteWenkuNovel(
        username: String,
        novelId: String,
    ) {
        val esnovel = es.client.getDocument(
            target = ElasticSearchDataSource.wenkuNovelIndexName,
            id = novelId,
        ).document<WenkuNovelMetadataEsModel>()
        mongo
            .wenkuNovelFavoriteCollection
            .insertOne(
                WenkuNovelFavoriteModel(
                    userId = getUserIdByUsername(username).toId(),
                    novelId = ObjectId(novelId).toId(),
                    createAt = Clock.System.now(),
                    updateAt = Instant.fromEpochSeconds(esnovel.updateAt),
                )
            )
    }

    suspend fun removeFavoriteWenkuNovel(
        username: String,
        novelId: String,
    ) {
        mongo
            .wenkuNovelFavoriteCollection
            .deleteOne(
                WenkuNovelFavoriteModel::userId eq getUserIdByUsername(username).toId(),
                WenkuNovelFavoriteModel::novelId eq ObjectId(novelId).toId(),
            )
    }
}