package infra.wenku.repository

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
import infra.wenku.UserFavoredWenkuNovelDbModel
import infra.wenku.WenkuNovelMetadata
import infra.wenku.WenkuNovelMetadataListItem
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

class WenkuNovelFavoredRepository(
    mongo: MongoClient,
) {
    private val userFavoredWenkuCollection =
        mongo.database.getCollection<UserFavoredWenkuNovelDbModel>(
            MongoCollectionNames.WENKU_FAVORITE,
        )

    suspend fun getFavoredId(
        userId: String,
        novelId: String,
    ): String? {
        return userFavoredWenkuCollection
            .find(
                and(
                    eq(
                        UserFavoredWenkuNovelDbModel::userId.field(), ObjectId(userId)
                    ),
                    eq(UserFavoredWenkuNovelDbModel::novelId.field(), ObjectId(novelId)),
                )
            )
            .firstOrNull()?.favoredId
    }

    suspend fun listFavoriteWenkuNovel(
        userId: String,
        favoredId: String,
        page: Int,
        pageSize: Int,
        sort: FavoredNovelListSort,
    ): Page<WenkuNovelMetadataListItem> {
        @Serializable
        data class PageModel(
            val total: Int = 0,
            val items: List<WenkuNovelMetadata>,
        )

        val sortProperty = when (sort) {
            FavoredNovelListSort.CreateAt -> UserFavoredWenkuNovelDbModel::createAt
            FavoredNovelListSort.UpdateAt -> UserFavoredWenkuNovelDbModel::updateAt
        }

        val doc = userFavoredWenkuCollection
            .aggregate<PageModel>(
                match(
                    and(
                        eq(UserFavoredWenkuNovelDbModel::userId.field(), ObjectId(userId)),
                        eq(UserFavoredWenkuNovelDbModel::favoredId.field(), favoredId),
                    )
                ),
                sort(
                    descending(sortProperty.field())
                ),
                facet(
                    Facet("count", count()),
                    Facet(
                        "items",
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            /* from = */ MongoCollectionNames.WENKU_NOVEL,
                            /* localField = */ UserFavoredWenkuNovelDbModel::novelId.field(),
                            /* foreignField = */ WenkuNovelMetadata::id.field(),
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
                total = doc.total.toLong(),
                items = doc.items.map {
                    WenkuNovelMetadataListItem(
                        id = it.id.toHexString(),
                        title = it.title,
                        titleZh = it.titleZh,
                        cover = it.cover,
                    )
                },
                pageSize = pageSize,
            )
        }
    }

    suspend fun countFavoredNovelByUserId(
        userId: String,
        favoredId: String,
    ): Long {
        return userFavoredWenkuCollection
            .countDocuments(
                and(
                    eq(UserFavoredWenkuNovelDbModel::userId.field(), ObjectId(userId)),
                    eq(UserFavoredWenkuNovelDbModel::favoredId.field(), favoredId),
                )
            )
    }

    suspend fun updateFavoredNovel(
        userId: ObjectId,
        novelId: ObjectId,
        favoredId: String,
        updateAt: Instant,
    ) {
        userFavoredWenkuCollection
            .replaceOne(
                and(
                    eq(UserFavoredWenkuNovelDbModel::userId.field(), userId),
                    eq(UserFavoredWenkuNovelDbModel::novelId.field(), novelId),
                ),
                UserFavoredWenkuNovelDbModel(
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
        userFavoredWenkuCollection
            .deleteOne(
                and(
                    eq(UserFavoredWenkuNovelDbModel::userId.field(), userId),
                    eq(UserFavoredWenkuNovelDbModel::novelId.field(), novelId),
                )
            )
    }
}