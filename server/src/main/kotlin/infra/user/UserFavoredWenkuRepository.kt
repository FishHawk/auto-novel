package infra.user

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Facet
import com.mongodb.client.model.UpdateOptions
import infra.DataSourceMongo
import infra.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate
import org.litote.kmongo.id.toId

class UserFavoredWenkuRepository(
    private val mongo: DataSourceMongo,
) {
    suspend fun updateFavored(
        userId: ObjectId,
        favored: List<UserFavored>,
    ) {
        mongo
            .userCollection
            .updateOne(
                User::id eq userId,
                setValue(User::favoredWenku, favored),
            )
    }

    suspend fun getFavoredId(
        userId: String,
        novelId: String,
    ): String? {
        return mongo.userFavoredWenkuCollection.findOne(
            UserFavoredWenkuNovelModel::userId eq ObjectId(userId).toId(),
            UserFavoredWenkuNovelModel::novelId eq ObjectId(novelId).toId(),
        )?.favoredId
    }

    suspend fun listFavoriteWenkuNovel(
        userId: String,
        favoredId: String,
        page: Int,
        pageSize: Int,
        sort: FavoredNovelListSort,
    ): Page<WenkuNovelMetadataOutline> {
        @Serializable
        data class NovelPage(val total: Int = 0, val items: List<WenkuNovelMetadata>)

        val sortProperty = when (sort) {
            FavoredNovelListSort.CreateAt -> UserFavoredWenkuNovelModel::createAt
            FavoredNovelListSort.UpdateAt -> UserFavoredWenkuNovelModel::updateAt
        }

        val doc = mongo
            .userFavoredWenkuCollection
            .aggregate<NovelPage>(
                match(
                    UserFavoredWenkuNovelModel::userId eq ObjectId(userId).toId(),
                    UserFavoredWenkuNovelModel::favoredId eq favoredId,
                ),
                sort(Document(sortProperty.path(), -1)),
                facet(
                    Facet("count", Aggregates.count()),
                    Facet(
                        "items",
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            from = mongo.wenkuNovelMetadataCollectionName,
                            localField = UserFavoredWenkuNovelModel::novelId.path(),
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
                pageSize = pageSize,
            )
        }
    }

    suspend fun countFavoredNovelByUserId(
        userId: String,
        favoredId: String,
    ): Long {
        return mongo
            .userFavoredWenkuCollection
            .countDocuments(
                and(
                    UserFavoredWenkuNovelModel::userId eq ObjectId(userId).toId(),
                    UserFavoredWenkuNovelModel::favoredId eq favoredId,
                )
            )
    }

    suspend fun updateFavoredNovel(
        userId: ObjectId,
        novelId: ObjectId,
        favoredId: String,
        updateAt: Instant,
    ) {
        mongo
            .userFavoredWenkuCollection
            .updateOne(
                and(
                    UserFavoredWenkuNovelModel::userId eq userId.toId(),
                    UserFavoredWenkuNovelModel::novelId eq novelId.toId(),
                ),
                UserFavoredWenkuNovelModel(
                    userId = userId.toId(),
                    novelId = novelId.toId(),
                    favoredId = favoredId,
                    createAt = Clock.System.now(),
                    updateAt = updateAt,
                ),
                UpdateOptions().upsert(true),
            )
    }

    suspend fun deleteFavoredNovel(
        userId: ObjectId,
        novelId: ObjectId,
    ) {
        mongo
            .userFavoredWenkuCollection
            .deleteOne(
                UserFavoredWenkuNovelModel::userId eq userId.toId(),
                UserFavoredWenkuNovelModel::novelId eq novelId.toId(),
            )
    }
}