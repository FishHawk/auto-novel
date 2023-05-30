package infra.model

import infra.web.WebNovelPatchHistoryRepository
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.litote.kmongo.and
import org.litote.kmongo.eq
import java.time.LocalDateTime

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

@Serializable
data class WebNovelPatchHistoryOutline(
    val providerId: String,
    @SerialName("bookId")
    val novelId: String,
    val titleJp: String,
    val titleZh: String?,
)

@Serializable
data class WebNovelPatchHistory(
    @Contextual @SerialName("_id")
    val id: ObjectId,
    val providerId: String,
    @SerialName("bookId")
    val novelId: String,
    val titleJp: String,
    val titleZh: String?,
    val patches: List<Patch>,
) {
    @Serializable
    data class TextChange(val jp: String, val zhOld: String?, val zhNew: String)

    @Serializable
    data class Patch(
        val uuid: String,
        val titleChange: TextChange?,
        val introductionChange: TextChange?,
        val glossary: Map<String, String>?,
        val tocChange: List<TextChange>,
        @Contextual val createAt: LocalDateTime,
    )

    companion object {
        fun byId(providerId: String, novelId: String): Bson {
            return and(
                WebNovelPatchHistory::providerId eq providerId,
                WebNovelPatchHistory::novelId eq novelId,
            )
        }
    }
}
