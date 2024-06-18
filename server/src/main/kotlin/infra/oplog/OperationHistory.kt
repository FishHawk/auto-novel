package infra.oplog

import infra.user.UserOutline
import infra.web.WebNovelTocItem
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
sealed interface Operation {
    @Serializable
    @SerialName("web-edit")
    data class WebEdit(
        val providerId: String,
        val novelId: String,
        val old: Data,
        val new: Data,
        val toc: List<Toc>,
    ) : Operation {
        @Serializable
        data class Data(
            val titleZh: String?,
            val introductionZh: String?,
        )

        @Serializable
        data class Toc(
            val jp: String,
            val old: String?,
            val new: String?,
        )
    }

    @Serializable
    @SerialName("web-edit-glossary")
    data class WebEditGlossary(
        val providerId: String,
        val novelId: String,
        val old: Map<String, String>,
        val new: Map<String, String>,
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

    @Serializable
    @SerialName("wenku-edit-glossary")
    data class WenkuEditGlossary(
        val novelId: String,
        val old: Map<String, String>,
        val new: Map<String, String>,
    ) : Operation

    @Serializable
    @SerialName("wenku-upload")
    data class WenkuUpload(
        val novelId: String,
        val volumeId: String,
    ) : Operation
}

@Serializable
data class OperationHistory(
    @Contextual @SerialName("_id") val id: ObjectId,
    @Contextual val operator: UserOutline,
    val operation: Operation,
    @Contextual val createAt: Instant,
)

@Serializable
data class WebNovelTocMergeHistory(
    @Contextual @SerialName("_id")
    val id: ObjectId,
    val providerId: String,
    @SerialName("bookId")
    val novelId: String,
    val tocOld: List<WebNovelTocItem>,
    val tocNew: List<WebNovelTocItem>,
    val reason: String,
)

// MongoDB
@Serializable
data class OperationHistoryModel(
    @Contextual @SerialName("_id") val id: ObjectId,
    @Contextual val operator: ObjectId,
    val operation: Operation,
    @Contextual val createAt: Instant,
)
