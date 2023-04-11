package data.web

import data.MongoDataSource
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId


class WebBookTocMergeHistoryRepository(
    private val mongo: MongoDataSource,
) {
    private val col
        get() = mongo.database.getCollection<TocMergedHistory>("toc-merge-history")

    @Serializable
    data class TocMergedHistory(
        @Contextual @SerialName("_id") val id: ObjectId,
        val providerId: String,
        val bookId: String,
        val tocOld: List<BookTocItem>,
        val tocNew: List<BookTocItem>,
        val reason: String,
    )

    suspend fun insert(
        providerId: String,
        bookId: String,
        tocOld: List<BookTocItem>,
        tocNew: List<BookTocItem>,
        reason: String,
    ) {
        col.insertOne(
            TocMergedHistory(
                id = ObjectId(),
                providerId = providerId,
                bookId = bookId,
                tocOld = tocOld,
                tocNew = tocNew,
                reason = reason,
            )
        )
    }
}