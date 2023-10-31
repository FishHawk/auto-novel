package infra.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class GpuCard(
    @Contextual @SerialName("_id") val id: ObjectId,
    val gpu: String,
    val endpoint: String,
)

@Serializable
data class GpuJob(
    @Contextual @SerialName("_id") val id: ObjectId,
    val task: String,
    val description: String,
    val workerId: String?,
    val submitter: String,
    @Contextual val createAt: Instant,
)

@Serializable
data class GpuJobResult(
    val task: String,
    val description: String,
    val workerId: String?,
    val submitter: String,
    val total: Int?,
    val finished: Int?,
    val error: Int?,
    @Contextual val createAt: Instant,
    @Contextual val finishAt: Instant,
)

@Serializable
data class SakuraFailCase(
    val providerId: String,
    val novelId: String,
    val chapterId: String,
    val prompt: String,
    val result: String,
)
