package infra.common

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Facet
import domain.entity.*
import infra.DataSourceMongo
import infra.DataSourceRedis
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
    suspend fun listArticleWithUser(
        page: Int,
        pageSize: Int,
        category: ArticleCategory,
    ): Page<ArticleSimplifiedWithUserReadModel> {
        @Serializable
        data class ArticlePage(
            val total: Int = 0,
            val items: List<ArticleSimplifiedWithUserReadModel>,
        )

        val doc = mongo
            .articleCollection
            .aggregate<ArticlePage>(
                match(Article::category eq category),
                facet(
                    Facet("count", Aggregates.count()),
                    Facet(
                        "items",
                        sort(descending(Article::pinned, Article::changeAt)),
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            from = mongo.userCollectionName,
                            localField = Article::user.path(),
                            foreignField = User::id.path(),
                            newAs = ArticleSimplifiedWithUserReadModel::user.path(),
                        ),
                        unwind(ArticleSimplifiedWithUserReadModel::user.path().projection),
                        project(
                            ArticleSimplifiedWithUserReadModel::id,
                            ArticleSimplifiedWithUserReadModel::title,
                            ArticleSimplifiedWithUserReadModel::category,
                            ArticleSimplifiedWithUserReadModel::locked,
                            ArticleSimplifiedWithUserReadModel::pinned,
                            ArticleSimplifiedWithUserReadModel::numViews,
                            ArticleSimplifiedWithUserReadModel::numComments,
                            ArticleSimplifiedWithUserReadModel::user / UserOutline::username,
                            ArticleSimplifiedWithUserReadModel::user / UserOutline::role,
                            ArticleSimplifiedWithUserReadModel::createAt,
                            ArticleSimplifiedWithUserReadModel::updateAt,
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

    suspend fun getArticleWithUser(
        id: ObjectId,
    ): ArticleWithUserReadModel? {
        return mongo
            .articleCollection
            .aggregate<ArticleWithUserReadModel>(
                match(Article::id eq id),
                lookup(
                    from = mongo.userCollectionName,
                    localField = Article::user.path(),
                    foreignField = User::id.path(),
                    newAs = ArticleWithUserReadModel::user.path(),
                ),
                unwind(ArticleWithUserReadModel::user.path().projection),
            )
            .first()
    }

    suspend fun getArticle(
        id: ObjectId,
    ): Article? =
        mongo
            .articleCollection
            .findOneById(id)

    suspend fun deleteArticle(
        id: ObjectId,
    ): Boolean =
        mongo
            .articleCollection
            .deleteOne(Article::id eq id)
            .run { deletedCount > 0 }

    suspend fun createArticle(
        title: String,
        content: String,
        category: ArticleCategory,
        userId: ObjectId,
    ): ObjectId {
        val now = Clock.System.now()
        return mongo
            .articleCollection
            .insertOne(
                Article(
                    id = ObjectId(),
                    title = title,
                    content = content,
                    category = category,
                    locked = false,
                    pinned = false,
                    numViews = 0,
                    numComments = 0,
                    user = userId.toId(),
                    createAt = now,
                    updateAt = now,
                    changeAt = now,
                )
            )
            .run { insertedId!!.asObjectId().value }
    }

    suspend fun increaseNumViews(
        userIdOrIp: String,
        id: ObjectId,
    ) = redis.withRateLimit("article:${userIdOrIp}:${id.toHexString()}") {
        mongo
            .articleCollection
            .updateOne(
                Article::id eq id,
                inc(Article::numViews, 1),
            )
    }

    suspend fun increaseNumComments(
        id: ObjectId
    ): Boolean =
        mongo
            .articleCollection
            .updateOne(
                Article::id eq id,
                combine(
                    inc(Article::numComments, 1),
                    setValue(Article::changeAt, Clock.System.now()),
                ),
            )
            .run { matchedCount > 0 }

    suspend fun updateTitleAndContent(
        id: ObjectId,
        title: String,
        content: String,
        category: ArticleCategory,
    ): Boolean {
        val now = Clock.System.now()
        return mongo
            .articleCollection
            .updateOne(
                Article::id eq id,
                combine(
                    setValue(Article::title, title),
                    setValue(Article::content, content),
                    setValue(Article::category, category),
                    setValue(Article::updateAt, now),
                    setValue(Article::changeAt, now),
                )
            )
            .run { matchedCount > 0 }
    }

    suspend fun updateArticlePinned(
        id: ObjectId,
        pinned: Boolean,
    ): Boolean =
        mongo
            .articleCollection
            .updateOne(
                Article::id eq id,
                setValue(Article::pinned, pinned),
            )
            .run { matchedCount > 0 }

    suspend fun updateArticleLocked(
        id: ObjectId,
        locked: Boolean,
    ): Boolean =
        mongo
            .articleCollection
            .updateOne(
                Article::id eq id,
                setValue(Article::locked, locked),
            )
            .run { matchedCount > 0 }
}