package infra.common

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Facet
import com.mongodb.client.model.Filters
import infra.DataSourceMongo
import infra.model.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate
import org.litote.kmongo.id.toId
import org.litote.kmongo.util.KMongoUtil

class OperationHistoryRepository(
    private val mongo: DataSourceMongo,
) {
    suspend fun list(
        page: Int,
        pageSize: Int,
        type: String,
    ): Page<OperationHistory> {
        @Serializable
        data class PageModel(val total: Int = 0, val items: List<OperationHistory>)

        val doc = mongo
            .operationHistoryCollection
            .aggregate<PageModel>(
                match(Filters.eq("${OperationHistoryModel::operation.path()}.___type", type)),
                facet(
                    Facet("count", Aggregates.count()),
                    Facet(
                        "items",
                        sort(descending(OperationHistoryModel::createAt)),
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            from = mongo.userCollectionName,
                            localField = OperationHistoryModel::operator.path(),
                            foreignField = User::id.path(),
                            newAs = OperationHistory::operator.path(),
                        ),
                        unwind(OperationHistory::operator.path().projection),
                        project(
                            OperationHistory::id,
                            OperationHistory::operator / UserOutline::username,
                            OperationHistory::operator / UserOutline::role,
                            OperationHistory::operation,
                            OperationHistory::createAt,
                        )
                    )
                ),
                project(
                    PageModel::total from arrayElemAt("count.count".projection, 0),
                    PageModel::items from "items".projection,
                ),
            )
            .first()
        return if (doc == null) {
            emptyPage()
        } else {
            Page(
                items = doc.items,
                total = doc.total.toLong(),
                pageSize = pageSize,
            )
        }
    }

    suspend fun create(
        operator: ObjectId,
        operation: Operation,
    ) {
        mongo
            .operationHistoryCollection
            .insertOne(
                OperationHistoryModel(
                    id = ObjectId(),
                    operator = operator.toId(),
                    operation = operation,
                    createAt = Clock.System.now(),
                )
            )
    }

    suspend fun delete(id: String) {
        mongo
            .operationHistoryCollection
            .deleteOneById(ObjectId(id))
    }

    suspend fun listMergeHistory(
        page: Int,
        pageSize: Int,
    ): Page<WebNovelTocMergeHistory> {
        val total = mongo
            .webNovelTocMergeHistoryCollection
            .countDocuments()
        val items = mongo
            .webNovelTocMergeHistoryCollection
            .find()
            .sort(KMongoUtil.toBson("{ _id: -1 }"))
            .skip(page * pageSize)
            .limit(pageSize)
            .toList()
        return Page(
            items = items,
            total = total,
            pageSize = pageSize,
        )
    }

    suspend fun deleteMergeHistory(id: String) {
        mongo
            .webNovelTocMergeHistoryCollection
            .deleteOneById(ObjectId(id))
    }
}