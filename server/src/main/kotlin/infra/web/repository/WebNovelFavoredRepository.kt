package infra.web.repository

import com.mongodb.client.model.Aggregates.*
import com.mongodb.client.model.Facet
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Projections.*
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts.descending
import infra.*
import infra.common.FavoredNovelListSort
import infra.common.Page
import infra.common.emptyPage
import infra.web.WebNovelFavoriteDbModel
import infra.web.WebNovel
import infra.web.WebNovelListItem
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

class WebNovelFavoredRepository(
    mongo: MongoClient,
) {
    private val userFavoredWebCollection =
        mongo.database.getCollection<WebNovelFavoriteDbModel>(
            MongoCollectionNames.WEB_FAVORITE,
        )

    suspend fun getFavoredId(
        userId: String,
        novelId: String,
    ): String? {
        return userFavoredWebCollection
            .find(
                and(
                    eq(WebNovelFavoriteDbModel::userId.field(), ObjectId(userId)),
                    eq(WebNovelFavoriteDbModel::novelId.field(), ObjectId(novelId)),
                )
            ).firstOrNull()?.favoredId
    }

    suspend fun listFavoredNovel(
        userId: String,
        favoredId: String?,
        page: Int,
        pageSize: Int,
        sort: FavoredNovelListSort,
    ): Page<WebNovelListItem> {
        @Serializable
        data class PageModel(
            val total: Int = 0,
            val items: List<WebNovel>,
        )

        val filterBson = if (favoredId == null) {
            eq(WebNovelFavoriteDbModel::userId.field(), ObjectId(userId))
        } else {
            and(
                eq(WebNovelFavoriteDbModel::userId.field(), ObjectId(userId)),
                eq(WebNovelFavoriteDbModel::favoredId.field(), favoredId),
            )
        }

        val sortBson = when (sort) {
            FavoredNovelListSort.CreateAt -> descending(WebNovelFavoriteDbModel::createAt.field())
            FavoredNovelListSort.UpdateAt -> descending(WebNovelFavoriteDbModel::updateAt.field())
        }

        val doc = userFavoredWebCollection
            .aggregate<PageModel>(
                match(filterBson),
                sort(sortBson),
                facet(
                    Facet("count", count()),
                    Facet(
                        "items",
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            /* from = */ MongoCollectionNames.WEB_NOVEL,
                            /* localField = */ WebNovelFavoriteDbModel::novelId.field(),
                            /* foreignField = */ WebNovel::id.field(),
                            /* as = */ "novel"
                        ),
                        unwind("\$novel"),
                        replaceRoot("\$novel"),
                    )
                ),
                project(
                    fields(
                        computed(PageModel::total.field(), arrayElemAt("count.count", 0)),
                        include(PageModel::items.field())
                    )
                ),
            )
            .firstOrNull()
        return if (doc == null) {
            emptyPage()
        } else {
            Page(
                items = doc.items.map { it.toOutline() },
                total = doc.total.toLong(),
                pageSize = pageSize,
            )
        }
    }

    suspend fun countFavoredNovelByUserId(
        userId: String,
        favoredId: String,
    ): Long {
        return userFavoredWebCollection
            .countDocuments(
                and(
                    eq(WebNovelFavoriteDbModel::userId.field(), ObjectId(userId)),
                    eq(WebNovelFavoriteDbModel::favoredId.field(), favoredId),
                )
            )
    }

    suspend fun updateFavoredNovel(
        userId: ObjectId,
        novelId: ObjectId,
        favoredId: String,
        updateAt: Instant,
    ) {
        userFavoredWebCollection
            .replaceOne(
                and(
                    eq(WebNovelFavoriteDbModel::userId.field(), userId),
                    eq(WebNovelFavoriteDbModel::novelId.field(), novelId),
                ),
                WebNovelFavoriteDbModel(
                    userId = userId,
                    novelId = novelId,
                    favoredId = favoredId,
                    createAt = Clock.System.now(),
                    updateAt = updateAt,
                ),
                ReplaceOptions().upsert(true),
            )
    }

    suspend fun deleteFavoredNovel(
        userId: ObjectId,
        novelId: ObjectId,
    ) {
        userFavoredWebCollection
            .deleteOne(
                and(
                    eq(WebNovelFavoriteDbModel::userId.field(), userId),
                    eq(WebNovelFavoriteDbModel::novelId.field(), novelId),
                )
            )
    }
}