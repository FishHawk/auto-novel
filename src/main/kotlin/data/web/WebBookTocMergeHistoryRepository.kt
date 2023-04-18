package data.web

import data.MongoDataSource
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.eq
import org.litote.kmongo.util.KMongoUtil.toBson

class WebBookTocMergeHistoryRepository(
    private val mongo: MongoDataSource,
) {
    private val col
        get() = mongo.database.getCollection<TocMergeHistory>("toc-merge-history")

    // List operations
    @Serializable
    data class TocMergedHistoryOutline(
        @Contextual @SerialName("_id") val id: ObjectId,
        val providerId: String,
        val bookId: String,
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
                TocMergedHistoryOutline::bookId,
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
        @Contextual @SerialName("_id") val id: ObjectId,
        val providerId: String,
        val bookId: String,
        val tocOld: List<BookTocItem>,
        val tocNew: List<BookTocItem>,
        val reason: String,
    )

    suspend fun get(id: String): TocMergeHistory? {
        return col.findOne(
            TocMergeHistory::id eq ObjectId(id),
        )
    }

    suspend fun delete(id: String) {
        col.deleteOne(
            TocMergeHistory::id eq ObjectId(id),
        )
    }

    suspend fun insert(
        providerId: String,
        bookId: String,
        tocOld: List<BookTocItem>,
        tocNew: List<BookTocItem>,
        reason: String,
    ) {
        col.insertOne(
            TocMergeHistory(
                id = ObjectId(),
                providerId = providerId,
                bookId = bookId,
                tocOld = tocOld,
                tocNew = tocNew,
                reason = reason,
            )
        )
    }

    suspend fun findOne(): TocMergeHistory? {
        return col.findOne()
    }
}