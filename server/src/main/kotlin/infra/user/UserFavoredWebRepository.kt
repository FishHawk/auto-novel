package infra.user

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Facet
import com.mongodb.client.model.ReplaceOptions
import domain.entity.*
import infra.DataSourceMongo
import infra.aggregate
import infra.web.toOutline
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.id.toId

class UserFavoredWebRepository(
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
                setValue(User::favoredWeb, favored)
            )
    }

    suspend fun getFavoredId(
        userId: String,
        novelId: String,
    ): String? {
        return mongo.userFavoredWebCollection.find(
            and(
                UserFavoredWebNovelModel::userId eq ObjectId(userId).toId(),
                UserFavoredWebNovelModel::novelId eq ObjectId(novelId).toId(),
            )
        ).firstOrNull()?.favoredId
    }

    suspend fun listFavoredNovel(
        userId: String,
        favoredId: String,
        page: Int,
        pageSize: Int,
        sort: FavoredNovelListSort,
    ): Page<WebNovelMetadataOutline> {
        @Serializable
        data class NovelPage(val total: Int = 0, val items: List<WebNovelMetadata>)

        val sortProperty = when (sort) {
            FavoredNovelListSort.CreateAt -> UserFavoredWebNovelModel::createAt
            FavoredNovelListSort.UpdateAt -> UserFavoredWebNovelModel::updateAt
        }

        val doc = mongo
            .userFavoredWebCollection
            .aggregate<NovelPage>(
                match(
                    UserFavoredWebNovelModel::userId eq ObjectId(userId).toId(),
                    UserFavoredWebNovelModel::favoredId eq favoredId,
                ),
                sort(Document(sortProperty.path(), -1)),
                facet(
                    Facet("count", Aggregates.count()),
                    Facet(
                        "items",
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            from = mongo.webNovelMetadataCollectionName,
                            localField = UserFavoredWebNovelModel::novelId.path(),
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
        return mongo
            .userFavoredWebCollection
            .countDocuments(
                and(
                    UserFavoredWebNovelModel::userId eq ObjectId(userId).toId(),
                    UserFavoredWebNovelModel::favoredId eq favoredId,
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
            .userFavoredWebCollection
            .replaceOne(
                and(
                    UserFavoredWebNovelModel::userId eq userId.toId(),
                    UserFavoredWebNovelModel::novelId eq novelId.toId(),
                ),
                UserFavoredWebNovelModel(
                    userId = userId.toId(),
                    novelId = novelId.toId(),
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
        mongo
            .userFavoredWebCollection
            .deleteOne(
                and(
                    UserFavoredWebNovelModel::userId eq userId.toId(),
                    UserFavoredWebNovelModel::novelId eq novelId.toId(),
                )
            )
    }
}