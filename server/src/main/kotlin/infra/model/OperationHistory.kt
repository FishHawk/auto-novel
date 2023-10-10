package infra.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.Id

@Serializable
sealed interface Operation {
    @Serializable
    @SerialName("wenku-upload")
    data class WenkuUpload(
        val novelId: String,
        val volumeId: String,
    ) : Operation

    @Serializable
    @SerialName("wenku-edit")
    data class WenkuEdit(
        val novelId: String,
        val old: Data?,
        val new: Data,
    ) : Operation {
        @Serializable
        data class Data(
            val title: String,
            val titleZh: String,
            val authors: List<String>,
            val artists: List<String>,
            val introduction: String,
        )
    }
}

@Serializable
data class OperationHistory(
    @Contextual @SerialName("_id") val id: ObjectId,
    @Contextual val operator: UserOutline,
    val operation: Operation,
    @Contextual val createAt: Instant,
)

@Serializable
data class OperationHistoryModel(
    @Contextual @SerialName("_id") val id: ObjectId,
    @Contextual val operator: Id<User>,
    val operation: Operation,
    @Contextual val createAt: Instant,
)
