package infra.common

import com.mongodb.client.model.CountOptions
import infra.DataSourceMongo
import infra.model.GpuJob
import org.bson.types.ObjectId
import org.litote.kmongo.and
import org.litote.kmongo.eq

class GpuJobRepository(
    private val mongo: DataSourceMongo,
) {
    suspend fun listJob(): List<GpuJob> {
        return mongo
            .gpuJobCollection
            .find()
            .toList()
    }

    suspend fun countJob(): Long {
        return mongo
            .gpuJobCollection
            .countDocuments()
    }

    suspend fun getJob(
        id: ObjectId,
    ): GpuJob? {
        return mongo
            .gpuJobCollection
            .findOne(
                GpuJob::id eq id
            )
    }

    suspend fun createJob(
        job: GpuJob,
    ): Boolean {
        val exist = mongo
            .gpuJobCollection
            .countDocuments(
                GpuJob::task eq job.task,
                CountOptions().limit(1),
            ) > 0
        if (exist) return false

        val result = mongo
            .gpuJobCollection
            .insertOne(job)
        return result.insertedId != null
    }

    suspend fun deleteJob(id: ObjectId): Boolean {
        val result = mongo
            .gpuJobCollection
            .deleteOne(
                and(
                    GpuJob::id eq id,
                    GpuJob::workerId eq null,
                ),
            )
        return result.deletedCount > 0
    }
}