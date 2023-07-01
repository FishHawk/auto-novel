package infra.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class WenkuNovelUploadHistory(
    @Contextual @SerialName("_id") val id: ObjectId,
    val novelId: String,
    val volumeId: String,
    val uploader: String,
    @Contextual val createAt: Instant,
)
