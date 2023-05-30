package infra.web

import infra.MongoDataSource
import infra.model.Page
import infra.model.WebNovelTocItem
import infra.model.WebNovelTocMergeHistory
import org.bson.types.ObjectId
import org.litote.kmongo.util.KMongoUtil.toBson

class WebNovelTocMergeHistoryRepository(
    private val mongo: MongoDataSource,
) {
    suspend fun list(
        page: Int,
        pageSize: Int,
    ): Page<WebNovelTocMergeHistory> {
        val total = mongo
            .webNovelTocMergeHistoryCollection
            .countDocuments()
        val items = mongo
            .webNovelTocMergeHistoryCollection
            .find()
            .sort(toBson("{ _id: -1 }"))
            .skip(page * pageSize)
            .limit(pageSize)
            .toList()
        return Page(items = items, total = total)
    }

    suspend fun get(id: String): WebNovelTocMergeHistory? {
        return mongo
            .webNovelTocMergeHistoryCollection
            .findOneById(ObjectId(id))
    }

    suspend fun delete(id: String) {
        mongo
            .webNovelTocMergeHistoryCollection
            .deleteOneById(ObjectId(id))
    }

    suspend fun insert(
        providerId: String,
        novelId: String,
        tocOld: List<WebNovelTocItem>,
        tocNew: List<WebNovelTocItem>,
        reason: String
    ) {
        mongo
            .webNovelTocMergeHistoryCollection
            .insertOne(
                WebNovelTocMergeHistory(
                    id = ObjectId(),
                    providerId = providerId,
                    novelId = novelId,
                    tocOld = tocOld,
                    tocNew = tocNew,
                    reason = reason,
                )
            )
    }
}