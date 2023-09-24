package infra.wenku

import infra.MongoDataSource
import infra.model.Page
import infra.model.WenkuNovelEditHistory
import kotlinx.datetime.Clock
import org.bson.types.ObjectId

class WenkuNovelEditHistoryRepository(
    private val mongo: MongoDataSource,
) {
    suspend fun list(
        page: Int,
        pageSize: Int,
    ): Page<WenkuNovelEditHistory> {
        val total = mongo
            .wenkuNovelEditHistoryCollection
            .countDocuments()
        val items = mongo
            .wenkuNovelEditHistoryCollection
            .find()
            .descendingSort()
            .skip(page * pageSize)
            .limit(pageSize)
            .toList()
        return Page(items = items, total = total)
    }

    suspend fun delete(id: String) {
        mongo
            .wenkuNovelEditHistoryCollection
            .deleteOneById(ObjectId(id))
    }

    suspend fun create(
        novelId: String,
        operator: String,
        old: WenkuNovelEditHistory.Data?,
        new: WenkuNovelEditHistory.Data,
    ) {
        mongo
            .wenkuNovelEditHistoryCollection
            .insertOne(
                WenkuNovelEditHistory(
                    id = ObjectId(),
                    novelId = novelId,
                    operator = operator,
                    old = old,
                    new = new,
                    createAt = Clock.System.now(),
                )
            )
    }
}