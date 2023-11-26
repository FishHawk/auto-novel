package infra.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class SakuraServer(
    @Contextual @SerialName("_id") val id: ObjectId,
    val username: String,
    val gpu: String,
    val endpoint: String,
    val isActive: Boolean = false,
)

@Serializable
data class SakuraJob(
    @Contextual @SerialName("_id") val id: ObjectId,
    val task: String,
    val description: String,
    val workerId: String?,
    val submitter: String,
    @Contextual val createAt: Instant,
)

@Serializable
data class SakuraJobResult(
    val task: String,
    val description: String,
    val workerId: String?,
    val submitter: String,
    val total: Int?,
    val finished: Int?,
    @Contextual val createAt: Instant,
    @Contextual val finishAt: Instant,
)

@Serializable
data class SakuraWebIncorrectCase(
    val providerId: String,
    val novelId: String,
    val chapterId: String,
    val uploader: String,
    val jp: String,
    val zh: String,
    val createAt: Instant,
)

@Serializable
data class SakuraWebFailCase(
    val providerId: String,
    val novelId: String,
    val chapterId: String,
    val prompt: String,
    val result: String,
)

@Serializable
data class SakuraWenkuFailCase(
    val novelId: String,
    val volumeId: String,
    val chapterId: String,
    val prompt: String,
    val result: String,
)
