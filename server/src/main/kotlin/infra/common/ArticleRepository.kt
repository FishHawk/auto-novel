package infra.common

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.Facet
import infra.DataSourceMongo
import infra.DataSourceRedis
import infra.model.*
import infra.withRateLimit
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate
import org.litote.kmongo.id.toId

class ArticleRepository(
    private val mongo: DataSourceMongo,
    private val redis: DataSourceRedis,
) {
    suspend fun listArticle(
        page: Int,
        pageSize: Int,
    ): Page<ArticleOutline> {
        @Serializable
        data class ArticlePage(val total: Int = 0, val items: List<ArticleOutline>)

        val doc = mongo
            .articleCollection
            .aggregate<ArticlePage>(
                facet(
                    Facet("count", Aggregates.count()),
                    Facet(
                        "items",
                        sort(descending(ArticleModel::pinned, ArticleModel::updateAt)),
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            from = mongo.userCollectionName,
                            localField = ArticleModel::user.path(),
                            foreignField = User::id.path(),
                            newAs = ArticleOutline::user.path(),
                        ),
                        unwind(ArticleOutline::user.path().projection),
                        project(
                            ArticleOutline::id,
                            ArticleOutline::title,
                            ArticleOutline::locked,
                            ArticleOutline::pinned,
                            ArticleOutline::numViews,
                            ArticleOutline::numComments,
                            ArticleOutline::user / UserOutline::username,
                            ArticleOutline::user / UserOutline::role,
                            ArticleOutline::createAt,
                            ArticleOutline::updateAt,
                        )
                    )
                ),
                project(
                    ArticlePage::total from arrayElemAt("count.count".projection, 0),
                    ArticlePage::items from "items".projection,
                )
            )
            .first()
        return if (doc == null) {
            emptyPage()
        } else {
            Page(
                items = doc.items,
                total = doc.total.toLong(),
                pageSize = pageSize,
            )
        }
    }

    suspend fun getArticle(
        id: ObjectId,
    ): Article? {
        return mongo
            .articleCollection
            .aggregate<Article>(
                match(ArticleModel::id eq id),
                lookup(
                    from = mongo.userCollectionName,
                    localField = ArticleModel::user.path(),
                    foreignField = User::id.path(),
                    newAs = Article::user.path(),
                ),
                unwind(Article::user.path().projection),
            )
            .first()
    }

    suspend fun isArticleBelongUser(
        id: ObjectId,
        userId: ObjectId,
    ): Boolean {
        val count = mongo
            .articleCollection
            .countDocuments(
                and(
                    ArticleModel::id eq id,
                    ArticleModel::user eq userId.toId()
                ),
                CountOptions().limit(1),
            )
        return count > 0L
    }

    suspend fun deleteArticle(
        id: ObjectId,
    ): Boolean {
        val deleteResult = mongo
            .articleCollection
            .deleteOne(ArticleModel::id eq id)
        val deleted = deleteResult.deletedCount > 0
        if (deleted) {
            mongo
                .commentCollection
                .deleteMany(CommentModel::site eq "article-${id.toHexString()}")
        }
        return deleted
    }

    suspend fun createArticle(
        title: String,
        content: String,
        userId: ObjectId,
    ): ObjectId {
        val now = Clock.System.now()
        val insertResult = mongo
            .articleCollection
            .insertOne(
                ArticleModel(
                    id = ObjectId(),
                    title = title,
                    content = content,
                    locked = false,
                    pinned = false,
                    numViews = 0,
                    numComments = 0,
                    user = userId.toId(),
                    createAt = now,
                    updateAt = now,
                )
            )
        return insertResult.insertedId!!.asObjectId().value
    }

    suspend fun increaseNumViews(
        userIdOrIp: String,
        id: ObjectId,
    ) = redis.withRateLimit("article:${userIdOrIp}:${id.toHexString()}") {
        mongo
            .articleCollection
            .updateOne(
                ArticleModel::id eq id,
                inc(ArticleModel::numViews, 1),
            )
    }

    suspend fun increaseNumComments(id: ObjectId) {
        mongo
            .articleCollection
            .updateOne(
                ArticleModel::id eq id,
                inc(ArticleModel::numComments, 1),
            )
    }

    suspend fun updateArticleTitleAndContent(
        id: ObjectId,
        title: String,
        content: String,
    ) {
        mongo
            .articleCollection
            .updateOne(
                ArticleModel::id eq id,
                combine(
                    setValue(ArticleModel::title, title),
                    setValue(ArticleModel::content, content),
                    setValue(ArticleModel::updateAt, Clock.System.now())
                )
            )
    }

    suspend fun updateArticlePinned(
        id: ObjectId,
        pinned: Boolean,
    ): Boolean {
        val updateResult = mongo
            .articleCollection
            .updateOne(
                ArticleModel::id eq id,
                setValue(ArticleModel::pinned, pinned),
            )
        return updateResult.matchedCount > 0
    }

    suspend fun updateArticleLocked(
        id: ObjectId,
        locked: Boolean,
    ): Boolean {
        val updateResult = mongo
            .articleCollection
            .updateOne(
                ArticleModel::id eq id,
                setValue(ArticleModel::locked, locked),
            )
        return updateResult.matchedCount > 0
    }
}