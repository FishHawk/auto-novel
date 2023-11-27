package infra.user

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Facet
import com.mongodb.client.model.UpdateOptions
import infra.DataSourceMongo
import infra.model.*
import infra.web.toOutline
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate
import org.litote.kmongo.id.toId

class UserReadHistoryWebRepository(
    private val mongo: DataSourceMongo,
) {
    suspend fun listReaderHistory(
        userId: String,
        page: Int,
        pageSize: Int,
    ): Page<WebNovelMetadataOutline> {
        @Serializable
        data class NovelPage(val total: Int = 0, val items: List<WebNovelMetadata>)

        val doc = mongo
            .userReadHistoryWebCollection
            .aggregate<NovelPage>(
                match(UserReadHistoryWebModel::userId eq ObjectId(userId).toId()),
                sort(Document(UserReadHistoryWebModel::createAt.path(), -1)),
                facet(
                    Facet("count", Aggregates.count()),
                    Facet(
                        "items",
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            from = mongo.webNovelMetadataCollectionName,
                            localField = UserReadHistoryWebModel::novelId.path(),
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

    suspend fun getReaderHistory(
        userId: String,
        novelId: String,
    ): UserReadHistoryWebModel? {
        return mongo
            .userReadHistoryWebCollection
            .findOne(
                and(
                    UserReadHistoryWebModel::userId eq ObjectId(userId).toId(),
                    UserReadHistoryWebModel::novelId eq ObjectId(novelId).toId(),
                ),
            )
    }

    suspend fun updateReadHistory(
        userId: ObjectId,
        novelId: ObjectId,
        chapterId: String,
    ) {
        mongo
            .userReadHistoryWebCollection
            .updateOne(
                and(
                    UserReadHistoryWebModel::userId eq userId.toId(),
                    UserReadHistoryWebModel::novelId eq novelId.toId(),
                ),
                UserReadHistoryWebModel(
                    userId = userId.toId(),
                    novelId = novelId.toId(),
                    chapterId = chapterId,
                    createAt = Clock.System.now(),
                ),
                UpdateOptions().upsert(true),
            )
    }

    suspend fun deleteReadHistory(
        userId: ObjectId,
        novelId: ObjectId,
    ) {
        mongo
            .userReadHistoryWebCollection
            .deleteOne(
                and(
                    UserReadHistoryWebModel::userId eq userId.toId(),
                    UserReadHistoryWebModel::novelId eq novelId.toId(),
                ),
            )
    }
}
