package infra.common

import com.mongodb.client.model.CountOptions
import infra.DataSourceMongo
import domain.entity.SakuraJob
import domain.entity.SakuraWebIncorrectCase
import org.bson.types.ObjectId
import org.litote.kmongo.and
import org.litote.kmongo.eq
import org.litote.kmongo.regex

class SakuraJobRepository(
    private val mongo: DataSourceMongo,
) {
    suspend fun listJob(): List<SakuraJob> {
        return mongo
            .sakuraJobCollection
            .find()
            .toList()
    }

    suspend fun countJob(): Long {
        return mongo
            .sakuraJobCollection
            .countDocuments()
    }

    suspend fun countSimilarJob(task: String): Long {
        return mongo
            .sakuraJobCollection
            .countDocuments(
                SakuraJob::task regex "^${task.substringBefore("?")}"
            )
    }

    suspend fun getJob(
        id: ObjectId,
    ): SakuraJob? {
        return mongo
            .sakuraJobCollection
            .findOne(
                SakuraJob::id eq id
            )
    }

    suspend fun createJob(
        job: SakuraJob,
    ): Boolean {
        val exist = mongo
            .sakuraJobCollection
            .countDocuments(
                SakuraJob::task eq job.task,
                CountOptions().limit(1),
            ) > 0
        if (exist) return false

        val result = mongo
            .sakuraJobCollection
            .insertOne(job)
        return result.insertedId != null
    }

    suspend fun deleteJob(id: ObjectId): Boolean {
        val result = mongo
            .sakuraJobCollection
            .deleteOne(
                and(
                    SakuraJob::id eq id,
                    SakuraJob::workerId eq null,
                ),
            )
        return result.deletedCount > 0
    }

    suspend fun createWebIncorrectCase(
        case: SakuraWebIncorrectCase,
    ) {
        mongo
            .sakuraWebIncorrectCaseCollection
            .insertOne(case)
    }
}