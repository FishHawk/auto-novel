package infra.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.litote.kmongo.and
import org.litote.kmongo.eq
import java.time.LocalDateTime

@Serializable
enum class WebNovelAttention {
    @SerialName("R15")
    R15,

    @SerialName("R18")
    R18,

    @SerialName("残酷描写")
    残酷描写,

    @SerialName("暴力描写")
    暴力描写,

    @SerialName("性描写")
    性描写,
}

@Serializable
enum class WebNovelType {
    @SerialName("连载中")
    连载中,

    @SerialName("已完结")
    已完结,

    @SerialName("短篇")
    短篇,
}

@Serializable
data class WebNovelMetadataOutline(
    val providerId: String,
    @SerialName("bookId")
    val novelId: String,
    val titleJp: String,
    val titleZh: String?,
    val type: WebNovelType?,
    val attentions: List<WebNovelAttention>,
    val keywords: List<String>,
    val total: Long = 0,
    val jp: Long = 0,
    val baidu: Long = 0,
    val youdao: Long = 0,
    val extra: String? = null,
)

@Serializable
class WebNovelMetadata(
    @Contextual @SerialName("_id") val id: ObjectId,
    val providerId: String,
    @SerialName("bookId")
    val novelId: String,
    val wenkuId: String? = null,
    val titleJp: String,
    val titleZh: String? = null,
    val authors: List<WebNovelAuthor>,
    val type: WebNovelType = WebNovelType.连载中,
    val attentions: List<WebNovelAttention> = emptyList(),
    val keywords: List<String> = emptyList(),
    val introductionJp: String,
    val introductionZh: String? = null,
    val glossaryUuid: String? = null,
    val glossary: Map<String, String> = emptyMap(),
    val toc: List<WebNovelTocItem>,
    // Translate state
    val jp: Long = 0,
    val baidu: Long = 0,
    val youdao: Long = 0,
    // Misc
    val visited: Long = 0,
    val pauseUpdate: Boolean = false,
    @Contextual val syncAt: LocalDateTime = LocalDateTime.now(),
    @Contextual val changeAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun byId(providerId: String, novelId: String): Bson =
            and(
                WebNovelMetadata::providerId eq providerId,
                WebNovelMetadata::novelId eq novelId,
            )
    }
}

@Serializable
data class WebNovelAuthor(
    val name: String,
    val link: String?,
)

@Serializable
data class WebNovelTocItem(
    val titleJp: String,
    val titleZh: String?,
    @SerialName("episodeId")
    val chapterId: String?,
    @Contextual val createAt: Instant? = null,
)

@Serializable
data class WebNovelChapter(
    val providerId: String,
    @SerialName("bookId")
    val novelId: String,
    @SerialName("episodeId")
    val chapterId: String,
    @SerialName("paragraphsJp")
    val paragraphs: List<String>,

    @SerialName("glossaryUuid")
    val baiduGlossaryUuid: String? = null,
    @SerialName("glossary")
    val baiduGlossary: Map<String, String> = emptyMap(),
    @SerialName("paragraphsZh")
    val baiduParagraphs: List<String>? = null,

    val youdaoGlossaryUuid: String? = null,
    val youdaoGlossary: Map<String, String> = emptyMap(),
    val youdaoParagraphs: List<String>? = null,
) {
    companion object {
        fun byId(providerId: String, novelId: String, chapterId: String): Bson {
            return and(
                WebNovelChapter::providerId eq providerId,
                WebNovelChapter::novelId eq novelId,
                WebNovelChapter::chapterId eq chapterId,
            )
        }

        fun byNovelId(providerId: String, novelId: String): Bson {
            return and(
                WebNovelChapter::providerId eq providerId,
                WebNovelChapter::novelId eq novelId,
            )
        }
    }
}