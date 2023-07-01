package infra.wenku

import infra.MongoDataSource
import infra.model.Page
import infra.model.WenkuNovelUploadHistory
import kotlinx.datetime.Clock
import org.bson.types.ObjectId
import org.litote.kmongo.util.KMongoUtil

class WenkuNovelUploadHistoryRepository(
    private val mongo: MongoDataSource,
) {
    suspend fun list(
        page: Int,
        pageSize: Int,
    ): Page<WenkuNovelUploadHistory> {
        val total = mongo
            .wenkuNovelUploadHistoryCollection
            .countDocuments()
        val items = mongo
            .wenkuNovelUploadHistoryCollection
            .find()
            .sort(KMongoUtil.toBson("{ _id: -1 }"))
            .skip(page * pageSize)
            .limit(pageSize)
            .toList()
        return Page(items = items, total = total)
    }

    suspend fun delete(id: String) {
        mongo
            .wenkuNovelUploadHistoryCollection
            .deleteOneById(ObjectId(id))
    }

    suspend fun insert(
        novelId: String,
        volumeId: String,
        uploader: String,
    ) {
        mongo
            .wenkuNovelUploadHistoryCollection
            .insertOne(
                WenkuNovelUploadHistory(
                    id = ObjectId(),
                    novelId = novelId,
                    volumeId = volumeId,
                    uploader = uploader,
                    createAt = Clock.System.now(),
                )
            )
    }
}