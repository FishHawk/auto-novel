package infra.web

import infra.MongoDataSource
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.litote.kmongo.eq
import org.litote.kmongo.util.KMongoUtil.toBson

private typealias TocItem = WebNovelMetadataRepository.NovelMetadata.TocItem

class WebNovelTocMergeHistoryRepository(
    private val mongo: MongoDataSource,
) {
    private val col
        get() = mongo.database.getCollection<TocMergeHistory>("toc-merge-history")

    companion object {
        private fun byId(id: String): Bson {
            return TocMergeHistory::id eq ObjectId(id)
        }
    }

    // List operations
    @Serializable
    data class TocMergedHistoryOutline(
        @Contextual @SerialName("_id")
        val id: ObjectId,
        val providerId: String,
        @SerialName("bookId")
        val novelId: String,
        val reason: String,
    )

    suspend fun list(
        page: Int,
        pageSize: Int,
    ): List<TocMergedHistoryOutline> {
        return col
            .withDocumentClass<TocMergedHistoryOutline>()
            .find()
            .sort(toBson("{ _id: -1 }"))
            .skip(page * pageSize)
            .limit(pageSize)
            .projection(
                TocMergedHistoryOutline::id,
                TocMergedHistoryOutline::providerId,
                TocMergedHistoryOutline::novelId,
                TocMergedHistoryOutline::reason,
            )
            .toList()
    }

    suspend fun count(): Long {
        return col.countDocuments()
    }

    // Element operations
    @Serializable
    data class TocMergeHistory(
        @Contextual @SerialName("_id")
        val id: ObjectId,
        val providerId: String,
        @SerialName("bookId")
        val novelId: String,
        val tocOld: List<TocItem>,
        val tocNew: List<TocItem>,
        val reason: String,
    )

    suspend fun findOne(id: String): TocMergeHistory? = col.findOne(byId(id))

    suspend fun findOne(): TocMergeHistory? = col.findOne()

    suspend fun deleteOne(id: String) = col.deleteOne(byId(id))

    suspend fun insertOne(
        providerId: String,
        novelId: String,
        tocOld: List<TocItem>,
        tocNew: List<TocItem>,
        reason: String,
    ) = col.insertOne(
        TocMergeHistory(
            id = ObjectId(),
            providerId = providerId,
            novelId = novelId,
            tocOld = tocOld,
            tocNew = tocNew,
            reason = reason,
        )
    )
}